import java.io.*;
import java.net.*;

public class Sender{

    public static void main(String[] args) throws Exception{

        //cmdline for sender: java Sender <rcv_ip> <rcv_data_port> <sender_ack_port> <input_file> <timeout_ms> [window_size]
        String rIP = args[0];
        int rDP = Integer.parseInt(args[1]);
        int sAP = Integer.parseInt(args[2]);
        String ip = args[3];
        int timeoutMs = Integer.parseInt(args[4]);
        int windowSize = 1;

        // Is window size provided for GBN
        if(args.length > 5){
            windowSize = Integer.parseInt(args[5]);
        }

        InetAddress myRIP = InetAddress.getByName(rIP);
        DatagramSocket mySocket = new DatagramSocket(sAP);

        // Set timeout for socket to handle retransmissions
        mySocket.setSoTimeout(timeoutMs);

        FileInputStream fileHandle = new FileInputStream(ip);

        // Handshake
        DSPacket sotPacket = new DSPacket(DSPacket.TYPE_SOT, 0, new byte[0]);
        byte[] sotData = sotPacket.toBytes();

        DatagramPacket sendDP = new DatagramPacket(sotData, sotData.length, myRIP, rDP);

        boolean handshakeComplete = false;

        // loop until handshake is successful
        while(!handshakeComplete){
            try {
                mySocket.send(sendDP);

                byte[] bigByte = new byte[128];
                DatagramPacket myDP = new DatagramPacket(bigByte, bigByte.length);

                mySocket.receive(myDP);

                DSPacket myPacket = new DSPacket(myDP.getData());

                int packetType = myPacket.getType();
                int packetSeqNum = myPacket.getSeqNum();

                // Expecting ACK for SOT with seqNum 0
                if(packetType == DSPacket.TYPE_ACK && packetSeqNum == 0){
                    System.out.println("Connection established.");
                    handshakeComplete = true;
                }
            } catch (SocketTimeoutException e) {
                // Timeout occurred, retransmit the SOT packet
                System.out.println("Handshake timeout, retransmission.");
            }
        }

        // Phase 2:

    }
}