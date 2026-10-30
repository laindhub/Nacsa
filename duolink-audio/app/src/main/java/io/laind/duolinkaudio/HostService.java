package io.laind.duolinkaudio;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HostService extends Service {
  public static final String START="duolink.START_HOST",TEST="duolink.TEST",STATUS="duolink.STATUS";
  public static final String VERSION="DUOLINK_PCM1";
  public static final int PORT=38451, RATE=48000, PACKET=3840;
  public static volatile boolean active=false,connected=false;
  public static volatile int level=0;
  public static volatile String pin="";
  private final AtomicBoolean working=new AtomicBoolean(false);
  private final ArrayBlockingQueue<byte[]> queue=new ArrayBlockingQueue<>(12);
  private volatile ServerSocket listener;
  private volatile Socket client;
  private volatile AudioRecord capture;
  private volatile MediaProjection projection;
  @Override public IBinder onBind(Intent i){return null;}

  @Override public int onStartCommand(Intent i,int flags,int id){
    if(i==null)return START_NOT_STICKY;
    if(TEST.equals(i.getAction())){
      if(working.get())new Thread(this::tone,"duolink-test").start();
      return START_NOT_STICKY;
    }
    if(!START.equals(i.getAction())||!working.compareAndSet(false,true))return START_NOT_STICKY;
    pin=i.getStringExtra("pin");
    int result=i.getIntExtra("permissionResult",Activity.RESULT_CANCELED);
    Intent consent=Build.VERSION.SDK_INT>=33 ? i.getParcelableExtra("consent",Intent.class) : i.getParcelableExtra("consent");
    if(pin==null||!pin.matches("[0-9]{6}")||result!=Activity.RESULT_OK||consent==null){
      working.set(false);stopSelf();return START_NOT_STICKY;
    }
    try{foreground();active=true;}
    catch(Exception e){working.set(false);stopSelf();return START_NOT_STICKY;}
    new Thread(()->initialize(result,consent),"duolink-host-capture").start();
    return START_NOT_STICKY;
  }
  private void foreground(){
    String ch="duolink-host";
    NotificationManager nm=getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel(ch,"DuoLink anfitrión",NotificationManager.IMPORTANCE_LOW));
    PendingIntent stop=PendingIntent.getService(this,3,
        new Intent(this,HostService.class).setAction("duolink.NOOP"),
        PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification n=new Notification.Builder(this,ch).setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle("DuoLink · compartiendo audio")
      .setContentText("Captura autorizada de audio por Wi-Fi")
      .setContentIntent(PendingIntent.getActivity(this,4,
        new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT))
      .setOngoing(true).build();
    startForeground(4001,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
  }
  private void initialize(int result,Intent consent){
    try {
      MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
      projection=manager.getMediaProjection(result,consent);
      if(projection==null)throw new IllegalStateException("Permiso de captura no disponible");
      projection.registerCallback(new MediaProjection.Callback(){
        @Override public void onStop(){if(working.get()){report("Android detuvo la captura");stopSelf();}}
      },new Handler(Looper.getMainLooper()));
      AudioPlaybackCaptureConfiguration config=new AudioPlaybackCaptureConfiguration.Builder(projection)
        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        .addMatchingUsage(AudioAttributes.USAGE_GAME).build();
      int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);
      if(min<=0)throw new IOException("Audio interno estéreo no disponible");
      capture=new AudioRecord.Builder()
        .setAudioFormat(new AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
          .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build())
        .setAudioPlaybackCaptureConfig(config)
        .setBufferSizeInBytes(Math.max(19200,min*2)).build();
      if(capture.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Falló AudioRecord");
      listener=new ServerSocket();
      listener.setReuseAddress(true);
      listener.bind(new InetSocketAddress(PORT));
      new Thread(this::accept,"duolink-host-socket").start();
      capture.startRecording();
      report("Escuchando el audio interno · iniciá Spotify");
      loop();
    }catch(Exception e){if(working.get())report("Error: "+e.getMessage());stopSelf();}
  }
  private void loop(){
    byte[] bytes=new byte[PACKET];
    long previous=0;
    while(working.get()){
      AudioRecord rec=capture;
      if(rec==null)break;
      int n=rec.read(bytes,0,bytes.length,AudioRecord.READ_BLOCKING);
      if(n<0){report("Error de captura "+n);break;}
      n-=n%4;
      if(n==0)continue;
      byte[] frame=new byte[n];System.arraycopy(bytes,0,frame,0,n);
      if(connected){
        if(!queue.offer(frame)){queue.poll();queue.offer(frame);}
      }
      long now=SystemClock.elapsedRealtime();
      if(now-previous>500){
        long power=0;
        for(int x=0;x+1<n;x+=2){
          int v=(short)((frame[x]&0xff)|((frame[x+1]&0xff)<<8));
          power+=(long)v*v;
        }
        level=(int)Math.min(100,Math.sqrt(power/(double)(n/2))*200/32768);
        report(level==0?"Sin señal capturable: Spotify puede bloquear el audio":
            "Señal de audio detectada");
        previous=now;
      }
    }
    if(working.get())stopSelf();
  }
  private void accept(){
    while(working.get()){
      Socket socket=null;
      try {
        socket=listener.accept();
        socket.setTcpNoDelay(true);socket.setSoTimeout(5000);
        DataInputStream input=new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        DataOutputStream output=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        String ver=input.readUTF(),candidate=input.readUTF();
        if(!VERSION.equals(ver)||!pin.equals(candidate)||connected){
          output.writeUTF("DENIED");output.flush();socket.close();continue;
        }
        client=socket;connected=true;queue.clear();
        output.writeUTF("OK");output.writeInt(RATE);output.writeInt(PACKET);output.flush();
        report("Parlante conectado");
        final Socket accepted=socket;
        new Thread(()->send(accepted,output),"duolink-host-send").start();
      }catch(Exception e){
        if(socket!=null)try{socket.close();}catch(Exception ignored){}
        if(working.get())report("Conexión fallida: "+e.getMessage());
      }
    }
  }
  private void send(Socket socket,DataOutputStream output){
    try{
      socket.setSoTimeout(0);
      while(working.get()&&!socket.isClosed()){
        byte[] frame=queue.poll(1500,TimeUnit.MILLISECONDS);
        if(frame==null)continue;
        output.writeInt(frame.length);output.write(frame);output.flush();
      }
    }catch(Exception ignored){}
    finally {
      if(client==socket){client=null;connected=false;queue.clear();report("Parlante desconectado");}
      try{socket.close();}catch(Exception ignored){}
    }
  }
  private void tone(){
    AudioTrack track=null;
    try{
      track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL).build())
        .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
        .setBufferSizeInBytes(PACKET*5).setTransferMode(AudioTrack.MODE_STREAM).build();
      track.play();
      byte[] data=new byte[PACKET];
      for(int f=0;f<50&&working.get();f++){
        for(int s=0;s<960;s++){
          double t=(f*960.0+s)/RATE;
          short v=(short)(Math.sin(2*Math.PI*440*t)*6000*Math.min(1,Math.min(t*15,(1-t)*15)));
          int pos=s*4;
          data[pos]=(byte)v;data[pos+1]=(byte)(v>>8);
          data[pos+2]=(byte)v;data[pos+3]=(byte)(v>>8);
        }
        track.write(data,0,data.length);
      }
    }catch(Exception e){report("No se pudo generar el tono: "+e.getMessage());}
    finally{if(track!=null){try{track.stop();}catch(Exception ignored){}track.release();}}
  }
  private void report(String message){
    Intent i=new Intent(STATUS).setPackage(getPackageName());
    i.putExtra("role","host").putExtra("message",message);
    sendBroadcast(i);
  }
  @Override public void onDestroy(){
    working.set(false);active=false;connected=false;pin="";level=0;
    if(listener!=null)try{listener.close();}catch(Exception ignored){}
    if(client!=null)try{client.close();}catch(Exception ignored){}
    AudioRecord c=capture;capture=null;
    if(c!=null){try{c.stop();}catch(Exception ignored){}c.release();}
    MediaProjection p=projection;projection=null;
    if(p!=null)try{p.stop();}catch(Exception ignored){}
    report("Anfitrión detenido");
    super.onDestroy();
  }
}
