import java.io.*;
import java.net.*;

public class Receiver{
    public static void main(String[] args) throws Exception{
        
        //cmdline for receiver: java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>
        String sIP = args[0];
        int sAP = Integer.parseInt(args[1]);

        int rDP = Integer.parseInt(args[2]);

        String op = args[3];
        int RN = Integer.parseInt(args[4]);

        //write throws exception in void main to get rid of error
        InetAddress mySIP = InetAddress.getByName(sIP);

        DatagramSocket mySocket = new DatagramSocket(rDP);

        FileOutputStream fileHandle = new FileOutputStream(op);

        int tempS = 1;
        int tempOrder = 0;
        int ackCount = 0;

        //infinte loop till mentioned otherwise, for our receive
        while(true){

            //create a DS-FTP packet as outlined in assignment guidelines (section 2 goes over thid)
            byte[] bigBeautifulByte = new byte[128];

            DatagramPacket myDP = new DatagramPacket(bigBeautifulByte, bigBeautifulByte.length);

            mySocket.receive(myDP);

            DSPacket myPacket = new DSPacket (myDP.getData());

            int packetType = myPacket.getType();

            int packetSeqNum = myPacket.getSeqNum();

            //at this point we have received, check the type

            if(packetType == DSPacket.TYPE_SOT){
                ackCount++;

                if(!ChaosEngine.shouldDrop(ackCount, RN)){
                    //helper function
                    sendAck(mySocket, mySIP, sAP, packetSeqNum);
                }

                continue;
            }

            if(packetType == DSPacket.TYPE_DATA){
                if(packetSeqNum == tempS){
                    fileHandle.write(myPacket.getPayload());

                    tempOrder = packetSeqNum;

                    tempS = (tempS + 1) % 128;
                }

                ackCount++;

                if(!ChaosEngine.shouldDrop(ackCount, RN)){
                    sendAck(mySocket, mySIP, sAP, tempOrder);
                }

                continue;
            }

            if(packetType == DSPacket.TYPE_EOT){
                ackCount ++;

                if(!ChaosEngine.shouldDrop(ackCount, RN)){
                    sendAck(mySocket, mySIP, sAP, packetSeqNum);
                    break;
                }
                
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