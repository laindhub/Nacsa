package io.laind.duolinkaudio;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SpeakerService extends Service {
  public static final String START="duolink.START_SPEAKER";
  public static volatile boolean active=false;
  private final AtomicBoolean working=new AtomicBoolean(false);
  private volatile Socket socket;
  private volatile AudioTrack player;
  private Thread worker;
  private String hostIp,pass;
  private int bufferMs;
  @Override public IBinder onBind(Intent i){return null;}
  @Override public int onStartCommand(Intent i,int flags,int id){
    if(i==null||!START.equals(i.getAction())||!working.compareAndSet(false,true))return START_NOT_STICKY;
    hostIp=i.getStringExtra("ip");pass=i.getStringExtra("pin");
    bufferMs=Math.max(60,Math.min(500,i.getIntExtra("buffer",120)));
    if(hostIp==null||pass==null||!pass.matches("[0-9]{6}")){
      working.set(false);stopSelf();return START_NOT_STICKY;
    }
    try{foreground();active=true;}
    catch(Exception e){working.set(false);stopSelf();return START_NOT_STICKY;}
    worker=new Thread(this::startReceiving,"duolink-speaker");worker.start();
    return START_NOT_STICKY;
  }
  private void foreground(){
    String ch="duolink-speaker";
    NotificationManager nm=getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel(ch,"DuoLink parlante",NotificationManager.IMPORTANCE_LOW));
    PendingIntent open=PendingIntent.getActivity(this,10,new Intent(this,MainActivity.class),
      PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification n=new Notification.Builder(this,ch).setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle("DuoLink · parlante")
      .setContentText("Recibiendo sonido por Wi-Fi").setContentIntent(open).setOngoing(true).build();
    startForeground(4002,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
  }
  private void startReceiving(){
    for(int attempt=0;attempt<10&&working.get();attempt++){
      try {
        receive();
      }catch(Exception e){
        if(working.get())status("Conexión interrumpida: "+e.getMessage());
      }finally{closeSocket();closePlayer();}
      if(!working.get())return;
      if(attempt<9){
        status("Reconectando ("+(attempt+1)+"/10)...");
        try{Thread.sleep(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
      }
    }
    if(working.get()){status("No se pudo conectar: comprobá IP y PIN");stopSelf();}
  }
  private void receive() throws IOException {
    Socket s=new Socket();socket=s;
    s.connect(new InetSocketAddress(hostIp,HostService.PORT),2500);
    s.setTcpNoDelay(true);s.setSoTimeout(8000);
    DataOutputStream out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
    DataInputStream in=new DataInputStream(new BufferedInputStream(s.getInputStream(),32768));
    out.writeUTF(HostService.VERSION);out.writeUTF(pass);out.flush();
    if(!"OK".equals(in.readUTF()))throw new IOException("PIN rechazado o anfitrión ocupado");
    int rate=in.readInt(),frame=in.readInt();
    if(rate!=48000||frame!=3840)throw new IOException("Formato de audio incompatible");
    int min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
    if(min<=0)throw new IOException("Salida de sonido no compatible");
    int bytes=Math.max(min*2,(rate*4*(bufferMs+100))/1000);
    AudioTrack track=new AudioTrack.Builder()
      .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
      .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
      .setBufferSizeInBytes(bytes).setTransferMode(AudioTrack.MODE_STREAM).build();
    if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new IOException("No se pudo iniciar el parlante");
    player=track;
    ArrayDeque<byte[]> prime=new ArrayDeque<>();
    int minFrames=Math.max(3,bufferMs/20);
    boolean started=false;
    status("Conectado · preparando audio...");
    while(working.get()){
      int len=in.readInt();
      if(len<=0||len>16384||len%4!=0)throw new IOException("Paquete PCM incorrecto");
      byte[] pcm=new byte[len];in.readFully(pcm);
      if(!started){
        prime.addLast(pcm);
        if(prime.size()<minFrames)continue;
        track.play();started=true;
        status("Reproduciendo audio · ajustá el volumen de este celular");
        while(!prime.isEmpty())write(track,prime.removeFirst());
      }else write(track,pcm);
    }
  }
  private void write(AudioTrack t,byte[] frame)throws IOException{
    int pos=0;
    while(pos<frame.length&&working.get()){
      int n=t.write(frame,pos,frame.length-pos,AudioTrack.WRITE_BLOCKING);
      if(n<=0)throw new IOException("Error de reproducción: "+n);
      pos+=n;
    }
  }
  private void status(String message){
    Intent i=new Intent(HostService.STATUS).setPackage(getPackageName());
    i.putExtra("role","speaker").putExtra("message",message);
    sendBroadcast(i);
  }
  private void closeSocket(){
    Socket s=socket;socket=null;
    if(s!=null)try{s.close();}catch(Exception ignored){}
  }
  private void closePlayer(){
    AudioTrack a=player;player=null;
    if(a!=null){try{a.pause();a.flush();}catch(Exception ignored){}a.release();}
  }
  @Override public void onDestroy(){
    working.set(false);active=false;
    closeSocket();closePlayer();
    if(worker!=null)worker.interrupt();
    status("Parlante desconectado");
    super.onDestroy();
  }
}
