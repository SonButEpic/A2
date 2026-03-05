import java.io.*;
import java.net.*;
import java.util.*;

public class Receiver{
    public static void main(String[] args) throws Exception{
        
        //cmdline command that will be used for the receiver: java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>
        String tempUserIPAddress = args[0];
        int mySenderAckPort = Integer.parseInt(args[1]);
        int receiverDataPort = Integer.parseInt(args[2]);
        
        String myOutputFile = args[3];
        int RN = Integer.parseInt(args[4]);

        int windowSize = 127; 
        /*removed the following code since we dont have a window size argument in cmdline for receiver
        if(args.length > 5){
            windowSize = Integer.parseInt(args[5]);
        }
        */ 

        //used to convert the sender ip which was a string into an inet address, this just makes it so we can use the InetAddress object to send UDP packets to the sender
        InetAddress myInetSIP = InetAddress.getByName(tempUserIPAddress);
        DatagramSocket mySocket = new DatagramSocket(receiverDataPort);
        FileOutputStream fileHandle = new FileOutputStream(myOutputFile);

        int expectedSeqNum = 0;
        
        int tempLastACKSent = 0;
        int ackCounter = 0;

        HashMap<Integer, byte[]> buffer = new HashMap<>(); //buffer is used to store out of order packets. We use as hash map to store them when they are out of order and for quick retrevial when our packet finally arrives


        while(true){

            //create a DS-FTP packet as outlined in assignment guidelines (section 2 goes over thid)
            byte[] bigBeautifulByte = new byte[DSPacket.MAX_PACKET_SIZE];

            DatagramPacket bytesBeautifulDatagramPacket = new DatagramPacket(bigBeautifulByte, bigBeautifulByte.length);
            
            //recieve our beautiful packet to be processef
            mySocket.receive(bytesBeautifulDatagramPacket);
            
            //once we get our beautiful packet we gotta convert it into a DSPacket object to be used
            DSPacket myBeautifulPacket = new DSPacket (bytesBeautifulDatagramPacket.getData());


            //store the type because there are 3 different types and get teh sequence num too
            int packetType = myBeautifulPacket.getType();
            int packetSeqNum = myBeautifulPacket.getSeqNum();

            // this here is the handshake where if sucessful add 1 to the ack counter
            if(packetType == DSPacket.TYPE_SOT){
                ackCounter++;

                if(!ChaosEngine.shouldDrop(ackCounter, RN)){
                    
                    DSPacket tempAck = new DSPacket(DSPacket.TYPE_ACK, packetSeqNum, null);
                    
                    byte[] tempData = tempAck.toBytes();
                    
                    DatagramPacket currPacket = new DatagramPacket(tempData, tempData.length, myInetSIP, mySenderAckPort);
                    
                    mySocket.send(currPacket);
                }

                continue;
            }
            
            
            if(packetType == DSPacket.TYPE_DATA){


                int difference = (packetSeqNum - expectedSeqNum + 128 ) % 128;
                boolean withinBoundaries = difference < windowSize;

                if (packetSeqNum == expectedSeqNum){

                    fileHandle.write(myBeautifulPacket.getPayload());
                    tempLastACKSent = expectedSeqNum;
                    
                    
                    expectedSeqNum = (expectedSeqNum + 1) % 128;

                    // a quick check to see if our packet aer now able to recieved
                    while (buffer.containsKey(expectedSeqNum)){
                        fileHandle.write(buffer.get(expectedSeqNum));
                        
                        buffer.remove(expectedSeqNum);
                        tempLastACKSent = expectedSeqNum;


                        
                        expectedSeqNum = (expectedSeqNum + 1) % 128;
                    }
            } else if (withinBoundaries && !buffer.containsKey(packetSeqNum)){
                // Out of order packt arrived in window. Buffer it fo rlater delivery
                buffer.put(packetSeqNum, myBeautifulPacket.getPayload());
            }

                ackCounter++;

                if(!ChaosEngine.shouldDrop(ackCounter, RN)){

                    DSPacket tempAck = new DSPacket(DSPacket.TYPE_ACK, tempLastACKSent, null);
                    byte[] tempData = tempAck.toBytes();
                    

                    DatagramPacket currPacket = new DatagramPacket(tempData, tempData.length, myInetSIP, mySenderAckPort);
                    
                    mySocket.send(currPacket);
                }
                continue;
            }

            if(packetType == DSPacket.TYPE_EOT){
                ackCounter ++;

                if(!ChaosEngine.shouldDrop(ackCounter, RN)){
                    
                    DSPacket tempAck = new DSPacket(DSPacket.TYPE_ACK, packetSeqNum, null);
                   
                    byte[] tempData = tempAck.toBytes();
                    
                    DatagramPacket currPacket = new DatagramPacket(tempData, tempData.length, myInetSIP, mySenderAckPort);
                    
                    mySocket.send(currPacket);


                    System.out.println("File transfer complete.");
                    break;
                }
                continue;
            }
        }
        fileHandle.close();
        mySocket.close();


    }
}