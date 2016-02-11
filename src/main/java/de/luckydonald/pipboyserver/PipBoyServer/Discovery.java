package de.luckydonald.pipboyserver.PipBoyServer;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

import static de.luckydonald.pipboyserver.Constants.DISCOVER_STRING;
import static de.luckydonald.pipboyserver.Constants.DISCOVER_UDP_PORT;
import static de.luckydonald.pipboyserver.Constants.discover_response;

/**
 * Created by luckydonald on 14.01.16.
 *
 * The GAME server.
 *
 */
public class Discovery implements Runnable {
    private String serverType;
    private boolean shouldStop = false;

    public Discovery() {
        this("PC");
    }
    public Discovery(String serverType){
        this.serverType = serverType;
        this.shouldStop = false;
    }

    @Override
    public void run() {
        while (!shouldStop) {
            DatagramSocket socket = null;
            try {
                //Keep a socket open to listen to all the UDP trafic that is destined for this port
                socket = new DatagramSocket(DISCOVER_UDP_PORT, InetAddress.getByName("0.0.0.0"));
                socket.setBroadcast(true);
                while (!shouldStop) {
                    System.out.println(">>>Ready to receive broadcast packets!");

                    //Receive a packet
                    byte[] recvBuf = new byte[22];
                    DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
                    socket.receive(packet);

                    //Packet received
                    System.out.println(">>>Discovery packet received from: " + packet.getAddress().getHostAddress());
                    System.out.println(">>>Packet received; data: " + new String(packet.getData()));
                    //See if the packet holds the right command (message)
                    String message = new String(packet.getData()).trim();
                    if (message.equals(DISCOVER_STRING)) {
                        //Send a response
                        byte[] response = discover_response(this.serverType).getBytes();
                        System.out.println(">>>Sending " + serverType + " packet: " + Arrays.toString(response));
                        DatagramPacket sendPacket = new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort());
                        socket.send(sendPacket);
                        System.out.println(">>>Sent packet to " + serverType + " " + sendPacket.getAddress().getHostAddress());
                    }
                }
            } catch (BindException e) {
                try {
                    System.out.println("Failed to bind to Discovery port. Retrying.");
                    e.printStackTrace();
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                if (socket != null) {
                    socket.close();
                }
            }
        }
    }

    public boolean shouldStop() {
        return shouldStop;
    }

    public void shouldStop(boolean shouldStop) {
        this.shouldStop = shouldStop;
    }

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }
}
