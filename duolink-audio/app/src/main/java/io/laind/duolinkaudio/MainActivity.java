package io.laind.duolinkaudio;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.*;

public final class MainActivity extends Activity implements DirectLink.Listener {
  private static final int REQ_AUDIO=20,REQ_PROJECTION=21,REQ_DIRECT=22;
  private static final int BG=0xff101618,PANEL=0xff1a2426,GREEN=0xff1ed760,WHITE=0xfff1f6f3,MUTED=0xffb2c2b9;
  private boolean host=true;
  private String pendingPin="",pendingSpeakerPin="";
  private int pendingBufferMs=120;
  private boolean connectionPending=false;
  private boolean groupOwnerReady=false;
  private String directMessage="Conexión Wi-Fi Direct aún no iniciada";
  private String hostStatus="Sin transmisión",speakerStatus="Sin conexión";
  private TextView status,signal,directStatus;
  private EditText pinField;
  private SeekBar delay;
  private LinearLayout peerBox;
  private List<WifiP2pDevice> nearby=new ArrayList<>();
  private DirectLink direct;
  private BroadcastReceiver updates;
  private Runnable pendingPermissionAction;

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    host=!SpeakerService.active;
    direct=new DirectLink(this,this);
    render();
  }
  @Override protected void onStart(){
    super.onStart();
    direct.register();
    updates=new BroadcastReceiver(){
      @Override public void onReceive(Context c,Intent i){
        String role=i.getStringExtra("role"),message=i.getStringExtra("message");
        if(message==null)return;
        if("host".equals(role))hostStatus=message;
        if("speaker".equals(role))speakerStatus=message;
        if(status!=null)status.setText(host?hostStatus:speakerStatus);
        if(signal!=null)signal.setText("Señal: "+HostService.level+"% · "+(HostService.connected?"Parlante enlazado":"Sin invitado"));
      }
    };
    IntentFilter f=new IntentFilter(HostService.STATUS);
    if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,f,Context.RECEIVER_NOT_EXPORTED);
    else registerReceiver(updates,f);
    if(allowedDirect())direct.refreshConnection();
  }
  @Override protected void onStop(){
    if(updates!=null){unregisterReceiver(updates);updates=null;}
    direct.unregister();
    super.onStop();
  }
  private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  private GradientDrawable shape(int fill){
    GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(16));return d;
  }
  private TextView text(String s,int size,int color,boolean bold){
    TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
    if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    return t;
  }
  private void add(LinearLayout parent,View child,int top){
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);
    parent.addView(child,p);
  }
  private TextView button(String label,boolean primary,Runnable click){
    TextView b=text(label,15,primary?BG:WHITE,true);
    b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(16),dp(12),dp(16));
    b.setBackground(shape(primary?GREEN:0xff324043));
    b.setOnClickListener(v->click.run());return b;
  }
  private EditText edit(String hint,String initial,int type){
    EditText e=new EditText(this);e.setSingleLine(true);e.setText(initial);e.setHint(hint);
    e.setInputType(type);e.setTextSize(17);e.setTextColor(WHITE);e.setHintTextColor(0xff92a7a0);
    e.setPadding(dp(14),dp(13),dp(14),dp(13));e.setBackground(shape(0xff0b1214));
    return e;
  }
  private void render(){
    ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(36),dp(20),dp(32));
    scroll.addView(root);setContentView(scroll);
    add(root,text("DUOLINK · DIRECT",13,GREEN,true),0);
    add(root,text("Dos celulares. Sin router.",26,WHITE,true),6);
    add(root,text("El segundo Android funciona como parlante cercano. No necesita Wi-Fi de casa ni una cuenta Spotify.",14,MUTED,false),9);
    LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(0);
    TextView a=button("Anfitrión",host,()->{host=true;render();});
    TextView b=button("Parlante",!host,()->{host=false;render();});
    LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(4);
    LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,1);right.leftMargin=dp(4);
    tabs.addView(a,left);tabs.addView(b,right);add(root,tabs,22);
    LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(16),dp(18),dp(16),dp(20));
    card.setBackground(shape(PANEL));add(root,card,18);
    if(host)hostUi(card);else speakerUi(card);
    add(root,text("IMPORTANTE PARA VIAJES",12,GREEN,true),20);
    add(root,text("Wi-Fi Direct funciona sin Internet. El anfitrión puede reproducir música descargada; usar 4G/5G simultáneamente depende del teléfono. Spotify puede impedir que Android capture su audio; el tono de prueba permite distinguirlo de un problema de conexión.",13,MUTED,false),7);
    add(root,button("Configuración de Wi-Fi",false,()->startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS))),15);
    add(root,button("Configuración de ubicación",false,()->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))),8);
  }
  private void directHeader(LinearLayout card){
    add(card,text("CONEXIÓN DIRECTA · SIN RED EXTERNA",12,GREEN,true),0);
    directStatus=text(directMessage,14,MUTED,false);
    add(card,directStatus,9);
    if(!direct.supported())add(card,text("Wi-Fi Direct no está disponible en este dispositivo.",13,0xffffb86b,false),10);
  }
  private void hostUi(LinearLayout card){
    add(card,text("Compartir audio",22,WHITE,true),0);
    add(card,text("1 · Crear grupo Wi-Fi Direct",14,MUTED,true),16);
    directHeader(card);
    add(card,button("Crear conexión cercana",true,()->withDirectPermission(()->direct.createHostGroup())),14);
    add(card,button("Cerrar grupo Wi-Fi Direct",false,()->withDirectPermission(()->{
      direct.removeGroup();groupOwnerReady=false;stopService(new Intent(this,HostService.class));
    })),8);
    add(card,text("2 · Activar transmisión de audio",14,MUTED,true),20);
    status=text(HostService.active?hostStatus:"Captura todavía desactivada",14,GREEN,false);add(card,status,7);
    String code=HostService.pin.isEmpty()?pendingPin:HostService.pin;
    add(card,text("PIN de sesión: "+(code.isEmpty()?"se genera al iniciar audio":code),18,WHITE,true),10);
    signal=text("Señal: "+HostService.level+"% · "+(HostService.connected?"Parlante enlazado":"Sin invitado"),13,MUTED,false);
    add(card,signal,8);
    add(card,button("Iniciar captura de audio",true,()->{
      if(!groupOwnerReady){toast("Creá primero el grupo Wi-Fi Direct");return;}
      if(!HostService.active)requestProjection();
      else toast("La captura ya está encendida");
    }),14);
    add(card,button("Probar tono de 1 segundo",false,()->{
      if(!HostService.active){toast("Primero iniciá la captura");return;}
      startService(new Intent(this,HostService.class).setAction(HostService.TEST));
    }),8);
    add(card,button("Abrir Spotify",false,this::openSpotify),8);
    add(card,button("Detener audio",false,()->{
      stopService(new Intent(this,HostService.class));hostStatus="Captura detenida";render();
    }),8);
    add(card,text("El segundo teléfono debe elegir el nombre de este Android y escribir el PIN mostrado aquí. Si Android pregunta si aceptás la conexión Wi-Fi Direct, confirmala.",13,MUTED,false),14);
  }
  private void speakerUi(LinearLayout card){
    add(card,text("Parlante inalámbrico",22,WHITE,true),0);
    add(card,text("1 · Descubrir anfitrión",14,MUTED,true),16);
    directHeader(card);
    if(!locationEnabled())add(card,text("Activá Ubicación en los ajustes para que Android descubra teléfonos cercanos.",13,0xffffb86b,false),10);
    add(card,button("Buscar celulares cercanos",true,()->withDirectPermission(()->{
      if(!locationEnabled()){toast("Activá Ubicación para buscar dispositivos");return;}
      direct.scan();
    })),14);
    peerBox=new LinearLayout(this);peerBox.setOrientation(1);
    add(card,peerBox,12);fillPeers();
    add(card,text("2 · PIN que aparece en el anfitrión",14,MUTED,true),20);
    pinField=edit("PIN de 6 dígitos",pendingSpeakerPin,InputType.TYPE_CLASS_NUMBER);
    pinField.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
    add(card,pinField,10);
    add(card,text("Búfer de recepción (menos cortes, mayor retardo)",12,MUTED,false),12);
    delay=new SeekBar(this);delay.setMax(22);delay.setProgress(Math.max(0,(pendingBufferMs-60)/20));add(card,delay,5);
    status=text(SpeakerService.active?speakerStatus:"Sin audio",14,GREEN,false);add(card,status,14);
    add(card,button("Desconectar parlante",false,()->{
      pendingSpeakerPin="";connectionPending=false;
      stopService(new Intent(this,SpeakerService.class));
      withDirectPermission(()->direct.removeGroup());
      speakerStatus="Desconectado";render();
    }),13);
    add(card,text("No uses IP manual. Cuando aparezca el anfitrión, escribí su PIN y tocá su nombre para conectarte automáticamente.",13,MUTED,false),13);
  }
  private void fillPeers(){
    if(peerBox==null)return;
    peerBox.removeAllViews();
    if(nearby.isEmpty()){
      add(peerBox,text("Aún no aparecen dispositivos. Tocá Buscar y acercá ambos teléfonos.",13,MUTED,false),0);return;
    }
    for(WifiP2pDevice device:nearby){
      String name=DirectLink.displayName(device);
      String state=device.status==WifiP2pDevice.AVAILABLE?"Disponible":device.status==WifiP2pDevice.CONNECTED?"Conectado":"Cercano";
      add(peerBox,button(name+"  ·  "+state,false,()->selectPeer(device)),7);
    }
  }
  private void selectPeer(WifiP2pDevice device){
    String pin=pinField==null?"":pinField.getText().toString().trim();
    if(!pin.matches("[0-9]{6}")){toast("Primero ingresá el PIN de seis dígitos del anfitrión");return;}
    pendingSpeakerPin=pin;
    pendingBufferMs=delay==null?120:60+delay.getProgress()*20;
    connectionPending=true;
    withDirectPermission(()->direct.connectTo(device));
  }
  @Override public void onPeers(List<WifiP2pDevice> peers){
    nearby=new ArrayList<>(peers);fillPeers();
  }
  @Override public void onStatus(String message){
    directMessage=message;
    if(directStatus!=null)directStatus.setText(message);
  }
  @Override public void onConnected(InetAddress owner,boolean amOwner){
    if(owner==null)return;
    if(amOwner){
      groupOwnerReady=true;
      onStatus("Grupo Wi-Fi Direct activo. Listo para recibir al parlante.");
    }else{
      onStatus("Enlace directo establecido con "+owner.getHostAddress());
      if(connectionPending&&!SpeakerService.active && pendingSpeakerPin.matches("[0-9]{6}")){
        connectionPending=false;
        Intent i=new Intent(this,SpeakerService.class).setAction(SpeakerService.START);
        i.putExtra("ip",owner.getHostAddress());
        i.putExtra("pin",pendingSpeakerPin);
        i.putExtra("buffer",pendingBufferMs);
        try{startForegroundService(i);speakerStatus="Conectando sonido por Wi-Fi Direct...";if(status!=null)status.setText(speakerStatus);}
        catch(Exception e){onStatus("Error al iniciar audio: "+e.getMessage());}
      }
    }
  }
  private boolean allowedDirect(){
    String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
    return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;
  }
  private void withDirectPermission(Runnable task){
    if(!direct.supported()){toast("Este Android no soporta Wi-Fi Direct");return;}
    if(allowedDirect()){task.run();return;}
    pendingPermissionAction=task;
    requestPermissions(new String[]{Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION},REQ_DIRECT);
  }
  private boolean locationEnabled(){
    LocationManager lm=getSystemService(LocationManager.class);
    return lm!=null&&lm.isLocationEnabled();
  }
  private void requestProjection(){
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;
    }
    pendingPin=String.format(Locale.US,"%06d",new SecureRandom().nextInt(1000000));
    MediaProjectionManager m=getSystemService(MediaProjectionManager.class);
    if(m==null){toast("Captura de audio no disponible");return;}
    try{startActivityForResult(m.createScreenCaptureIntent(),REQ_PROJECTION);}
    catch(Exception e){toast("No se pudo solicitar captura: "+e.getMessage());}
  }
  @Override public void onRequestPermissionsResult(int request,String[] names,int[] grant){
    super.onRequestPermissionsResult(request,names,grant);
    boolean yes=grant.length>0&&grant[0]==PackageManager.PERMISSION_GRANTED;
    if(request==REQ_AUDIO){
      if(yes)requestProjection();else toast("Se necesita permiso de grabar audio");
    }else if(request==REQ_DIRECT){
      Runnable task=pendingPermissionAction;pendingPermissionAction=null;
      if(yes&&task!=null)task.run();else toast("Autorizá dispositivos cercanos para Wi-Fi Direct");
    }
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==REQ_PROJECTION){
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
  private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
