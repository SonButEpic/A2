import java.io.*;
import java.net.*;
import java.util.*;

public class Sender{

    public static void main(String[] args) throws Exception{

        //cmdline for that will be used by the Sender <rcv_ip> <rcv_data_port> <sender_ack_port> <input_file> <timeout_ms> [window_size]
        String tempUserIPAddress = args[0];
        int receiverDataPort = Integer.parseInt(args[1]);
        int mySenderAckPort = Integer.parseInt(args[2]);
        String myInputFile = args[3];
        
        int timeoutMs = Integer.parseInt(args[4]);
        int windowSize = 1;

        if(args.length > 5){
            windowSize = Integer.parseInt(args[5]);
        }

        //turn the string of our ip address into an inet object. wiull use this to create packets later for our sender
        InetAddress myInetReceiverIP = InetAddress.getByName(tempUserIPAddress);
        DatagramSocket myDatagramSocketWalkEmDown = new DatagramSocket(mySenderAckPort);

        myDatagramSocketWalkEmDown.setSoTimeout(timeoutMs);

        FileInputStream fileHandle = new FileInputStream(myInputFile);

        DSPacket sotPacket = new DSPacket(DSPacket.TYPE_SOT, 0, new byte[0]);
        //here we throw our packet into the byte array
        byte[] sotData = sotPacket.toBytes();

        DatagramPacket currPacket = new DatagramPacket(sotData, sotData.length, myInetReceiverIP, receiverDataPort);

        boolean handshakeComplete = false;

        long startTime = System.currentTimeMillis();

        // we are gonna keep looping until the handshake is finaly done to move on to the data transfer section. In here we prepare for buffer , wait for ack, adn error handeling if things blow up
        while(!handshakeComplete){
            try {
                myDatagramSocketWalkEmDown.send(currPacket);

                byte[] bigBeautifulByte = new byte[DSPacket.MAX_PACKET_SIZE];
                DatagramPacket bytesBeautifulDatagramPacket = new DatagramPacket(bigBeautifulByte, bigBeautifulByte.length);
                myDatagramSocketWalkEmDown.receive(bytesBeautifulDatagramPacket);

                DSPacket myBeautifulPacket = new DSPacket(bytesBeautifulDatagramPacket.getData());

                int packetType = myBeautifulPacket.getType();
                int packetSeqNum = myBeautifulPacket.getSeqNum();

                if(packetType == DSPacket.TYPE_ACK && packetSeqNum == 0){
                    System.out.println("Connection established.");
                    handshakeComplete = true;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Handshake timeout, retransmission.");
            }
        }
        // TODO: 03/02/26: Transfer has been tested with Stop-and-Wait and is working. Must replace/update Phase 2/3 with GBN Logic.

        List <DSPacket> allPackets = new ArrayList<>(); //a list will be used to store all of that packets for transmission
        int currentSeqNum = 1; // The first data packet will have seqNum 1
        
        byte[] buffer = new byte[DSPacket.MAX_PAYLOAD_SIZE];
        int bytesRead;


        // read the file in chunks of up to 124 bytes
        while((bytesRead = fileHandle.read(buffer)) != -1){

            //if reading less than 124 bytes ensure to size the payload correctly
            byte[] payload = new byte[bytesRead];
            
            System.arraycopy(buffer, 0, payload, 0, bytesRead);
            
            allPackets.add(new DSPacket(DSPacket.TYPE_DATA, currentSeqNum, payload));
            

            currentSeqNum = (currentSeqNum + 1) % 128;
        }

        int base = 0;
        int nextSeq = 0;
        int timeoutCt = 0;

        System.out.println("Limit Test: Loaded " + allPackets.size() + " packets into memory.");

        //we use a while loop to send all of the packets until we get an ack for these packets and can move on from ts
        while(base < allPackets.size()){
            
            List<DSPacket> windowPackets = new ArrayList<>();
            //run back a loop BUT this time we are collecting the packets ;) (for this window tho)
            while(nextSeq < base + windowSize && nextSeq < allPackets.size()){
                
                windowPackets.add(allPackets.get(nextSeq));
                
                nextSeq++;
            }

            //run back anotherr loop but use a for loop to iterate through our packets in humble chunks of 4 this time
            for(int i = 0; i < windowPackets.size(); i+= 4){
                
                List<DSPacket> chunkOdata = new ArrayList<>();
                
                for(int j = 0; j < 4 && i + j < windowPackets.size(); j++){
                    chunkOdata.add(windowPackets.get(i + j));  
                }

                List<DSPacket> mutlatedChunk = ChaosEngine.permutePackets(chunkOdata);

                //one more for loop, we gotta send the mutlated chunks and scrable some packets
                for (DSPacket p : mutlatedChunk){
                    
                    byte[] tempData = p.toBytes();
                    
                    DatagramPacket currDataPacket = new DatagramPacket(tempData, tempData.length, myInetReceiverIP, receiverDataPort);
                    
                    myDatagramSocketWalkEmDown.send(currDataPacket);
                }
            }
            try {
                while (true) { 
                    
                    byte[] ackBuffer = new byte[DSPacket.MAX_PACKET_SIZE];
                    
                    DatagramPacket ackDP = new DatagramPacket(ackBuffer, ackBuffer.length);
                    
                myDatagramSocketWalkEmDown.receive(ackDP);
                    
                    DSPacket ackPacket = new DSPacket(ackDP.getData());

                    if(ackPacket.getType() == DSPacket.TYPE_ACK){
                        int receivedAckSeq = ackPacket.getSeqNum();
                        int newBase = base;

                        // Check if cumulative ACK advances the window
                        for (int k = base; k < nextSeq; k++){
                            
                            if (allPackets.get(k).getSeqNum() == receivedAckSeq){
                                newBase = k + 1;
                            }
                        }

                        // If window moves forward, reset timeout ctr
                        if(newBase > base){
                            
                            base = newBase;
                            
                            timeoutCt = 0; 
                            
                            break;
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                timeoutCt++;
                
                if (timeoutCt >= 3){
                    System.out.println("Unable to transfer the file.");
                    
                    System.exit(1);
                }
                
                System.out.println("Timeout, retransmittiing.");
                
                nextSeq = base;
                
            }
        }

        // determine the EOT seqNum based on last packet sent, default 1 if no packet sent
        int eotSeqNum = 1; 
        
        if (!allPackets.isEmpty()){
            eotSeqNum = (allPackets.get(allPackets.size() - 1).getSeqNum() + 1) % 128; // EOT seqNum is one past the last data packet
        }

        DSPacket eotPacket = new DSPacket(DSPacket.TYPE_EOT, eotSeqNum, null);
        
        byte[] eotBytes = eotPacket.toBytes();
        
        DatagramPacket sendEotDP = new DatagramPacket(eotBytes, eotBytes.length, myInetReceiverIP, receiverDataPort);
    
        boolean eotAckReceived = false;
        
        int eotTimeoutCt = 0;

        while(!eotAckReceived){
            try {
                
                myDatagramSocketWalkEmDown.send(sendEotDP);
                byte[] ackBuffer = new byte[DSPacket.MAX_PACKET_SIZE];
               
                DatagramPacket ackDP = new DatagramPacket(ackBuffer, ackBuffer.length);
                
                myDatagramSocketWalkEmDown.receive(ackDP);
                
                DSPacket ackPacket = new DSPacket(ackDP.getData());

                if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == eotSeqNum){
                    eotAckReceived = true;
                    
                    System.out.println("File transfer complete.");
                }
            } 
            catch (SocketTimeoutException e) {
                eotTimeoutCt++;
                
                if(eotTimeoutCt >= 3){
                    System.out.println("Unable to transfer the file.");
                    
                    System.exit(1);
                }
                
                System.out.println("Timeout, retransmitting EOT.");
            }

        }

        long endTime = System.currentTimeMillis();
        
        double totalTimeSec = (endTime - startTime) / 1000.0;
        
        System.out.printf("Total transfer time: %.2f seconds%n", totalTimeSec);

        fileHandle.close();
        
        myDatagramSocketWalkEmDown.close();
    }
}