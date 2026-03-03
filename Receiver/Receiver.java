import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver{
    public static void main(String[] args) throws Exception{
        
        //cmdline for receiver: java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>
        String sIP = args[0];
        int sAP = Integer.parseInt(args[1]);
        int rDP = Integer.parseInt(args[2]);
        String op = args[3];
        int RN = Integer.parseInt(args[4]);

        int windowSize = 1; // default to stop-and-wait
        if(args.length > 5){
            windowSize = Integer.parseInt(args[5]);
        }

        InetAddress mySIP = InetAddress.getByName(sIP);
        DatagramSocket mySocket = new DatagramSocket(rDP);
        FileOutputStream fileHandle = new FileOutputStream(op);

        int expectedSeqNum = 1;
        int lastAckSent = 0; // Tracks cumulative ACK for GBN
        int ackCount = 0;

        HashMap<Integer, byte[]> buffer = new HashMap<>(); // Buffer out of order packs in GBN

        //infinte loop till mentioned otherwise, for our receive
        while(true){

            //create a DS-FTP packet as outlined in assignment guidelines (section 2 goes over thid)
            byte[] bigBeautifulByte = new byte[128];

            DatagramPacket myDP = new DatagramPacket(bigBeautifulByte, bigBeautifulByte.length);
            mySocket.receive(myDP);
            DSPacket myPacket = new DSPacket (myDP.getData());

            int packetType = myPacket.getType();
            int packetSeqNum = myPacket.getSeqNum();

            // Handshake and ACK for SOT
            if(packetType == DSPacket.TYPE_SOT){
                ackCount++;

                if(!ChaosEngine.shouldDrop(ackCount, RN)){
                    //helper function
                    sendAck(mySocket, mySIP, sAP, packetSeqNum);
                }

                continue;
            }
            
            // Process Data
            if(packetType == DSPacket.TYPE_DATA){
                // Check if packet is in the window
                boolean inWindow = false;
                for (int i = 0; i < windowSize; i++){
                    if (packetSeqNum == (expectedSeqNum + i) % 128){
                        inWindow = true;
                        break;
                    }
                }
                if (packetSeqNum == expectedSeqNum){

                // Packet arrived, deliver in order
                fileHandle.write(myPacket.getPayload());
                lastAckSent = expectedSeqNum;
                expectedSeqNum = (expectedSeqNum + 1) % 128;

                // Check buffer for next expected packets
                while (buffer.containsKey(expectedSeqNum)){
                    fileHandle.write(buffer.get(expectedSeqNum));
                    buffer.remove(expectedSeqNum); // Remove from buffer after write
                    lastAckSent = expectedSeqNum;
                    expectedSeqNum = (expectedSeqNum + 1) % 128;
                }
            } else if (inWindow && !buffer.containsKey(packetSeqNum)){
                // Out of order packt arrived in window. Buffer it fo rlater delivery
                buffer.put(packetSeqNum, myPacket.getPayload());
            }

                ackCount++;

                if(!ChaosEngine.shouldDrop(ackCount, RN)){
                    sendAck(mySocket, mySIP, sAP, lastAckSent);
                }
                continue;
            }

            if(packetType == DSPacket.TYPE_EOT){
                ackCount ++;

                if(!ChaosEngine.shouldDrop(ackCount, RN)){
                    sendAck(mySocket, mySIP, sAP, packetSeqNum);
                    System.out.println("File transfer complete.");
                    break;
                }
                // if ACK is droppde loop continues so sender can retransmit EOT
                continue;
            }
        }
        fileHandle.close();
        mySocket.close();

        //done at this point

    }

    private static void sendAck(DatagramSocket ts, InetAddress myIP, int myPort, int seqNum) throws Exception{
        DSPacket helpAck = new DSPacket(DSPacket.TYPE_ACK, seqNum, null);

        byte[] tempData = helpAck.toBytes();

        DatagramPacket dPacket = new DatagramPacket(tempData, tempData.length, myIP, myPort);

        ts.send(dPacket);

        //been sent
    }
}