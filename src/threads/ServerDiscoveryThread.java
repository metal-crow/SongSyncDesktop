package threads;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import main.Desktop_Server;

public class ServerDiscoveryThread extends Thread {
    
    DatagramSocket socket;
    private boolean listen=true;

    //listen for discover server requests udp packets
    //respond with ip the server is running on
    @Override
    public void run() {
        try {
            //Keep a socket open to listen to all the UDP trafic that is destined for this port
            socket = new DatagramSocket(9091, InetAddress.getByName("0.0.0.0"));
            socket.setBroadcast(true);
        }catch(IOException e){
            Desktop_Server.gui.current_status("Couldnt open Discovery thread listener.\n",2);
            e.printStackTrace();
            listen=false;
            return;
        }

        while(listen) {
            try {
                //Receive a packet
                byte[] recvBuf = new byte[150];
                DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                socket.receive(packet);
                
                //check packet data
                String message = new String(packet.getData()).trim();
                if(message.equals("Discover_SongSyncServer_Request")){
                    //respond with ip
                    String server_ip=InetAddress.getLocalHost().getHostAddress();
                    DatagramPacket sendPacket = new DatagramPacket(server_ip.getBytes(), server_ip.getBytes().length, packet.getAddress(), packet.getPort());
                    socket.send(sendPacket);
                }
            } catch (IOException e) {
                if(listen){
                    e.printStackTrace();
                }
            }
        }
    }

    public void stop_connection() {
        listen=false;
        socket.close();
    }

}
