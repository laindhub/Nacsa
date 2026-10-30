package io.laind.duolinkaudio;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;

public final class MainActivity extends Activity {
  private static final int AUDIO_REQUEST=20, PROJECTION_REQUEST=21;
  private static final int BG=0xff101618, PANEL=0xff1a2426, GREEN=0xff1ed760, WHITE=0xfff1f6f3, MUTED=0xffb2c2b9;
  private boolean host=true;
  private String pendingPin="";
  private TextView status, signal;
  private EditText ipField,pinField;
  private SeekBar delay;
  private BroadcastReceiver updates;
  private String hostStatus="Preparado para transmitir", speakerStatus="Esperando al anfitrión";

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    host=!SpeakerService.active;
    render();
  }
  @Override protected void onStart() {
    super.onStart();
    updates=new BroadcastReceiver() {
      @Override public void onReceive(Context c,Intent i) {
        String role=i.getStringExtra("role");
        String message=i.getStringExtra("message");
        if(message==null) return;
        if("host".equals(role)) hostStatus=message;
        if("speaker".equals(role)) speakerStatus=message;
        if(status!=null) status.setText(host ? hostStatus : speakerStatus);
        if(signal!=null && host) signal.setText("Señal capturada: "+HostService.level+"%   |   "+(HostService.connected?"Parlante conectado":"Sin invitado"));
      }
    };
    IntentFilter f=new IntentFilter(HostService.STATUS);
    if(Build.VERSION.SDK_INT>=33) registerReceiver(updates,f,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(updates,f);
  }
  @Override protected void onStop() {
    if(updates!=null){ unregisterReceiver(updates); updates=null; }
    super.onStop();
  }
  private int dp(int d){ return (int)(d*getResources().getDisplayMetrics().density+.5f); }
  private GradientDrawable shape(int fill) {
    GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(15)); return d;
  }
  private TextView txt(String value,int size,int color,boolean bold) {
    TextView t=new TextView(this); t.setText(value);t.setTextColor(color);t.setTextSize(size);
    if(bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    return t;
  }
  private void add(LinearLayout parent,View child,int top) {
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);
  }
  private TextView action(String name,boolean primary,Runnable f) {
    TextView t=txt(name,16,primary?BG:WHITE,true);
    t.setGravity(Gravity.CENTER);t.setPadding(dp(12),dp(17),dp(12),dp(17));
    t.setBackground(shape(primary?GREEN:0xff324043));
    t.setOnClickListener(v->f.run());return t;
  }
  private EditText field(String hint,String value,int type) {
    EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value);e.setTextSize(17);
    e.setInputType(type);e.setTextColor(WHITE);e.setHintTextColor(0xff92a7a0);
    e.setPadding(dp(14),dp(13),dp(14),dp(13));e.setBackground(shape(0xff0b1214));return e;
  }
  private void render() {
    ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);sc.setFillViewport(true);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(38),dp(20),dp(34));
    sc.addView(root);setContentView(sc);
    add(root,txt("DUOLINK AUDIO",14,GREEN,true),0);
    add(root,txt("Dos celulares, una canción.",26,WHITE,true),6);
    add(root,txt("Sin Spotify Jam. El invitado solo recibe sonido por Wi-Fi.",14,MUTED,false),8);
    LinearLayout roles=new LinearLayout(this);roles.setOrientation(0);
    TextView a=action("Anfitrión",host,()->{host=true;render();});
    TextView b=action("Parlante",!host,()->{host=false;render();});
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
    p.rightMargin=dp(5);roles.addView(a,p);
    p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(5);roles.addView(b,p);
    add(root,roles,22);
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(17),dp(19),dp(17),dp(19));panel.setBackground(shape(PANEL));
    add(root,panel,18);
    if(host) showHost(panel); else showSpeaker(panel);
    add(root,txt("COMPATIBILIDAD",12,GREEN,true),22);
    add(root,txt("Captura únicamente el sonido que Android autoriza a compartir. Spotify puede bloquear su audio; en ese caso el anfitrión reproduce, pero el invitado recibe silencio.",13,MUTED,false),8);
  }
  private void showHost(LinearLayout p) {
    add(p,txt("Transmitir sonido",22,WHITE,true),0);
    status=txt(HostService.active?hostStatus:"Sin iniciar",14,GREEN,false);add(p,status,10);
    add(p,txt("IP del anfitrión: "+localIp(),15,WHITE,true),16);
    add(p,txt("PIN: "+(HostService.active?HostService.pin:"se genera al iniciar"),20,WHITE,true),10);
    signal=txt("Señal capturada: "+HostService.level+"%   |   "+(HostService.connected?"Conectado":"Sin invitado"),13,MUTED,false);
    add(p,signal,9);
    add(p,action(HostService.active?"Transmisión iniciada":"Iniciar transmisión",true,()->{
      if(!HostService.active) requestProjection();else toast("Ya está activo");
    }),18);
    add(p,action("Abrir Spotify",false,this::openSpotify),10);
    add(p,action("Probar sonido de 1 segundo",false,()->{
      if(!HostService.active){toast("Primero iniciá la transmisión");return;}
      startService(new Intent(this,HostService.class).setAction(HostService.TEST));
    }),10);
    add(p,action("Detener transmisión",false,()->{
      stopService(new Intent(this,HostService.class));hostStatus="Transmisión detenida";render();
    }),10);
    add(p,txt("Conectá ambos teléfonos a la misma Wi-Fi, iniciá la transmisión y copiá la IP y el PIN en el segundo.",13,MUTED,false),14);
  }
  private void showSpeaker(LinearLayout p) {
    add(p,txt("Usar como parlante",22,WHITE,true),0);
    status=txt(SpeakerService.active?speakerStatus:"Sin conexión",14,GREEN,false);add(p,status,10);
    ipField=field("IP del anfitrión (192.168.x.x)",getPreferences(0).getString("hostIp",""),InputType.TYPE_CLASS_TEXT);
    add(p,ipField,16);
    pinField=field("PIN de 6 dígitos","",InputType.TYPE_CLASS_NUMBER);
    pinField.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
    add(p,pinField,10);
    add(p,txt("Búfer de recepción (más alto = menos cortes)",13,MUTED,false),13);
    delay=new SeekBar(this);delay.setMax(22);delay.setProgress(3);add(p,delay,6);
    add(p,action("Conectar parlante",true,()->{
      String ip=ipField.getText().toString().trim(),pin=pinField.getText().toString().trim();
      try {
        InetAddress addr=InetAddress.getByName(ip);
        if(!(addr instanceof Inet4Address)||!addr.isSiteLocalAddress())throw new Exception();
      }catch(Exception e){toast("Necesitás una IP IPv4 privada de Wi-Fi");return;}
      if(!pin.matches("[0-9]{6}")){toast("PIN incorrecto");return;}
      if(SpeakerService.active){toast("Desconectá el parlante anterior");return;}
      getPreferences(0).edit().putString("hostIp",ip).apply();
      Intent i=new Intent(this,SpeakerService.class).setAction(SpeakerService.START);
      i.putExtra("ip",ip);i.putExtra("pin",pin);i.putExtra("buffer",60+delay.getProgress()*20);
      try{startForegroundService(i);speakerStatus="Conectando a "+ip;status.setText(speakerStatus);}
      catch(Exception ex){toast("No se pudo iniciar: "+ex.getMessage());}
    }),17);
    add(p,action("Desconectar",false,()->{
      stopService(new Intent(this,SpeakerService.class));speakerStatus="Desconectado";render();
    }),10);
    add(p,txt("Este celular no necesita Spotify ni una segunda cuenta Premium.",13,MUTED,false),15);
  }
  private void requestProjection() {
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_REQUEST);return;
    }
    pendingPin=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1000000));
    try{
      MediaProjectionManager m=getSystemService(MediaProjectionManager.class);
      startActivityForResult(m.createScreenCaptureIntent(),PROJECTION_REQUEST);
    }catch(Exception e){toast("Permiso no disponible: "+e.getMessage());}
  }
  @Override public void onRequestPermissionsResult(int requestCode,String[] perms,int[] grants){
    super.onRequestPermissionsResult(requestCode,perms,grants);
    if(requestCode==AUDIO_REQUEST){
      if(grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)requestProjection();
      else toast("Se necesita el permiso de captura de audio");
    }
  }
  @Override protected void onActivityResult(int code,int result,Intent data){
    super.onActivityResult(code,result,data);
    if(code==PROJECTION_REQUEST){
      if(result!=RESULT_OK||data==null){toast("Captura cancelada");return;}
      Intent i=new Intent(this,HostService.class).setAction(HostService.START);
      i.putExtra("permissionResult",result);i.putExtra("consent",data);i.putExtra("pin",pendingPin);
      try{startForegroundService(i);hostStatus="Iniciando transmisión...";render();}
      catch(Exception e){toast("No se pudo iniciar: "+e.getMessage());}
    }
  }
  private void openSpotify(){
    Intent i=getPackageManager().getLaunchIntentForPackage("com.spotify.music");
    if(i==null)i=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://open.spotify.com"));
    try{startActivity(i);}catch(Exception e){toast("No se pudo abrir Spotify");}
  }
  private String localIp(){
    try{
      Enumeration<NetworkInterface> it=NetworkInterface.getNetworkInterfaces();
      while(it.hasMoreElements()){
        NetworkInterface n=it.nextElement();
        if(!n.isUp()||n.isLoopback())continue;
        Enumeration<InetAddress> addr=n.getInetAddresses();
        while(addr.hasMoreElements()){
          InetAddress a=addr.nextElement();
          if(a instanceof Inet4Address&&a.isSiteLocalAddress()&&n.getName().startsWith("wlan"))return a.getHostAddress();
        }
      }
    }catch(Exception ignored){}
    return "No disponible · conectá Wi-Fi";
  }
  private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
}
