package io.laind.duolinkaudio;

import android.content.*;
import android.net.wifi.p2p.*;
import android.net.wifi.WpsInfo;
import android.os.*;
import java.net.InetAddress;
import java.util.*;

public final class DirectLink {
  public interface Listener {
    void onStatus(String status);
    void onPeers(List<WifiP2pDevice> peers);
    void onConnected(InetAddress groupOwnerAddress, boolean amGroupOwner);
  }

  private final Context context;
  private final Listener listener;
  private final WifiP2pManager manager;
  private WifiP2pManager.Channel channel;
  private boolean registered=false;
  private boolean enabled=true;
  private boolean hasGroup=false;
  private final BroadcastReceiver receiver=new BroadcastReceiver(){
    @Override public void onReceive(Context ctx,Intent intent) {
      String action=intent.getAction();
      if(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action))refreshPeers();
      if(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action))refreshConnection();
      if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)){
        enabled=intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,-1)==WifiP2pManager.WIFI_P2P_STATE_ENABLED;
        if(!enabled)notifyStatus("Wi-Fi Direct está desactivado. Activá Wi-Fi.");
        else refreshConnection();
      }
    }
  };

  public DirectLink(Context ctx,Listener l){
    context=ctx;
    listener=l;
    manager=(WifiP2pManager)ctx.getSystemService(Context.WIFI_P2P_SERVICE);
    if(manager!=null)channel=manager.initialize(ctx,Looper.getMainLooper(),()->{
      channel=manager.initialize(ctx,Looper.getMainLooper(),null);
      notifyStatus("Canal Wi-Fi Direct reiniciado; reintentá conectar.");
    });
  }
  public boolean supported(){
    return manager!=null && channel!=null
      && context.getPackageManager().hasSystemFeature("android.hardware.wifi.direct");
  }
  public void register(){
    if(registered||!supported())return;
    IntentFilter filter=new IntentFilter();
    filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
    filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
    filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    // Android 13+ requires an explicit receiver export mode for non-exclusive broadcasts.
    if(Build.VERSION.SDK_INT>=33) context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
    else context.registerReceiver(receiver,filter);
    registered=true;
    refreshConnection();
  }
  public void unregister(){
    if(registered){context.unregisterReceiver(receiver);registered=false;}
  }
  private void notifyStatus(String text){listener.onStatus(text);}
  public void refreshConnection(){
    if(!supported())return;
    try{
      manager.requestConnectionInfo(channel,info->{
        if(info!=null && info.groupFormed && info.groupOwnerAddress!=null){
          hasGroup=true;
          listener.onConnected(info.groupOwnerAddress,info.isGroupOwner);
        }else if(hasGroup){
          hasGroup=false;
          notifyStatus("Wi-Fi Direct desconectado.");
        }
      });
    }catch(SecurityException e){notifyStatus("Permiso Wi-Fi Direct faltante.");}
      catch(Exception e){notifyStatus("Error Wi-Fi Direct: "+e.getMessage());}
  }
  public void createHostGroup(){
    if(!supported()){notifyStatus("Este Android no informa compatibilidad con Wi-Fi Direct.");return;}
    if(!enabled){notifyStatus("Activá el Wi-Fi y reintentá.");return;}
    try{
      notifyStatus("Creando conexión directa, sin router...");
      manager.createGroup(channel,new WifiP2pManager.ActionListener(){
        @Override public void onSuccess(){notifyStatus("Grupo Wi-Fi Direct creado. El segundo celular ya puede buscarte.");refreshConnection();}
        @Override public void onFailure(int reason){notifyStatus("No se pudo crear el grupo ("+reasonText(reason)+"). Si quedó otro grupo, cerralo y reintentá.");}
      });
    }catch(SecurityException e){notifyStatus("Autorizá dispositivos cercanos para crear el grupo.");}
     catch(Exception e){notifyStatus("Error al crear grupo: "+e.getMessage());}
  }
  public void scan(){
    if(!supported()){notifyStatus("Wi-Fi Direct no está disponible en este teléfono.");return;}
    if(!enabled){notifyStatus("Activá el Wi-Fi antes de buscar.");return;}
    try{
      notifyStatus("Buscando teléfonos cercanos por Wi-Fi Direct...");
      manager.discoverPeers(channel,new WifiP2pManager.ActionListener(){
        @Override public void onSuccess(){refreshPeers();}
        @Override public void onFailure(int reason){notifyStatus("Error de búsqueda ("+reasonText(reason)+"). Revisá Wi-Fi y Ubicación.");}
      });
    }catch(SecurityException e){notifyStatus("Falta permiso de dispositivos cercanos/ubicación.");}
     catch(Exception e){notifyStatus("No se puede buscar: "+e.getMessage());}
  }
  public void refreshPeers(){
    if(!supported())return;
    try{
      manager.requestPeers(channel,peers->{
        ArrayList<WifiP2pDevice> items=new ArrayList<>(peers.getDeviceList());
        Collections.sort(items,(a,b)->{
          String x=a.deviceName==null?"":a.deviceName;
          String y=b.deviceName==null?"":b.deviceName;
          return x.compareToIgnoreCase(y);
        });
        listener.onPeers(items);
        if(items.isEmpty())notifyStatus("No se encontraron equipos. Comprobá que el anfitrión haya creado su grupo.");
      });
    }catch(SecurityException e){notifyStatus("Falta autorización de dispositivos cercanos.");}
     catch(Exception e){notifyStatus("No se pudo listar: "+e.getMessage());}
  }
  public void connectTo(WifiP2pDevice device){
    if(device==null||!supported())return;
    try{
      WifiP2pConfig config=new WifiP2pConfig();
      config.deviceAddress=device.deviceAddress;
      config.wps.setup=WpsInfo.PBC;
      config.groupOwnerIntent=0; // invitee: prefer existing host group
      notifyStatus("Conectando a "+displayName(device)+". Aceptá el aviso del sistema si aparece.");
      manager.connect(channel,config,new WifiP2pManager.ActionListener(){
        @Override public void onSuccess(){notifyStatus("Solicitud enviada. Esperando enlace Wi-Fi Direct...");refreshConnection();}
        @Override public void onFailure(int reason){notifyStatus("Falló la conexión ("+reasonText(reason)+"). Volvé a buscar.");}
      });
    }catch(SecurityException e){notifyStatus("Permiso cercano/ubicación requerido.");}
     catch(Exception e){notifyStatus("Error al conectar: "+e.getMessage());}
  }
  public void removeGroup(){
    if(!supported())return;
    try{
      manager.removeGroup(channel,new WifiP2pManager.ActionListener(){
        @Override public void onSuccess(){hasGroup=false;notifyStatus("Grupo Wi-Fi Direct cerrado.");}
        @Override public void onFailure(int code){notifyStatus("No se pudo cerrar el grupo ("+reasonText(code)+").");}
      });
    }catch(Exception e){notifyStatus("No se pudo cerrar: "+e.getMessage());}
  }
  public static String displayName(WifiP2pDevice d){
    String n=d.deviceName;
    return n==null||n.trim().isEmpty()?"Android cercano":n;
  }
  private static String reasonText(int code){
    if(code==WifiP2pManager.BUSY)return "módulo ocupado";
    if(code==WifiP2pManager.P2P_UNSUPPORTED)return "no compatible";
    if(code==WifiP2pManager.ERROR)return "error del sistema";
    return "código "+code;
  }
}
