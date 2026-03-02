import java.io.*;
import java.net.*;
import java.util.*;


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

        long startTime = System.currentTimeMillis();

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
        // TODO: 03/02/26: Transfer has been tested with Stop-and-Wait and is working. Must replace/update Phase 2/3 with GBN Logic.

        // Phase 2: Data Transfer
        List <DSPacket> allPackets = new ArrayList<>();
        int currentSeqNum = 1; // The first data packet will have seqNum 1
        byte[] buffer = new byte[124]; // MAX_PAYLOAD_SIZE
        int bytesRead;


        // Read the file in chunks of up to 124 bytes.
        while((bytesRead = fileHandle.read(buffer)) != -1){

            //If reading less than 124 bytes, size the payload correctly.
            byte[] payload = new byte[bytesRead];
            System.arraycopy(buffer, 0, payload, 0, bytesRead);
            allPackets.add(new DSPacket(DSPacket.TYPE_DATA, currentSeqNum, payload));
            currentSeqNum = (currentSeqNum + 1) % 128;
        }

        int base = 0;
        int nextSeq = 0;
        int timeoutCt = 0;

        while(base < allPackets.size()){
            
            // Collect packets in the current window
            List<DSPacket> windowPackets = new ArrayList<>();
            while(nextSeq < base + windowSize && nextSeq < allPackets.size()){
                windowPackets.add(allPackets.get(nextSeq));
                nextSeq++;
            }

            for(int i = 0; i < windowPackets.size(); i+= 4){
                List<DSPacket> chunk = new ArrayList<>();
                for(int j = 0; j < 4 && i + j < windowPackets.size(); j++){
                    chunk.add(windowPackets.get(i + j));  
                }

                List<DSPacket> permutedChunk = ChaosEngine.permutePackets(chunk);

                for (DSPacket p : permutedChunk){
                    byte[] pBytes = p.toBytes();
                    DatagramPacket dataDP = new DatagramPacket(pBytes, pBytes.length, myRIP, rDP);
                    mySocket.send(dataDP);
                }
            }
            // Await ACKs for the window
            try {
                while (true) { 
                    byte[] ackBuffer = new byte[128];
                    DatagramPacket ackDP = new DatagramPacket(ackBuffer, ackBuffer.length);
                    mySocket.receive(ackDP);
                    DSPacket ackPacket = new DSPacket(ackDP.getData());

                    if(ackPacket.getType() == DSPacket.TYPE_ACK){
                        int receivedAckSeq = ackPacket.getSeqNum();
                        int newBase = base;

                        // Check if cumulative ACK advances the window
                        for (int k = base; k < nextSeq; k++){
                            if (allPackets.get(k).getSeqNum() == receivedAckSeq){
                                newBase = k + 1; // Move base one past the ACKed packet
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
                // Retransmit entire window from the base
                nextSeq = base;
            }
        }

        // Start of Teardown

        // Determine the EOT seqNum based on last packet sent, default 1 if no packet sent
        int eotSeqNum = 1; 
        if (!allPackets.isEmpty()){
            eotSeqNum = (allPackets.get(allPackets.size() - 1).getSeqNum() + 1) % 128; // EOT seqNum is one past the last data packet
        }

        DSPacket eotPacket = new DSPacket(DSPacket.TYPE_EOT, eotSeqNum, null);
        byte[] eotBytes = eotPacket.toBytes();
        DatagramPacket sendEotDP = new DatagramPacket(eotBytes, eotBytes.length, myRIP, rDP);
    
        boolean eotAckReceived = false;
        int eotTimeoutCt = 0;

        while(!eotAckReceived){
            try {
                
                mySocket.send(sendEotDP);
                byte[] ackBuffer = new byte[128];
                DatagramPacket ackDP = new DatagramPacket(ackBuffer, ackBuffer.length);
                mySocket.receive(ackDP);
                DSPacket ackPacket = new DSPacket(ackDP.getData());

                if(ackPacket.getType() == DSPacket.TYPE_ACK && ackPacket.getSeqNum() == eotSeqNum){
                    eotAckReceived = true;
                    System.out.println("File transfer complete.");
                }
            } catch (SocketTimeoutException e) {
                eotTimeoutCt++;
                if(eotTimeoutCt >= 3){
                    System.out.println("Unable to transfer the file.");
                    System.exit(1);
                }
                System.out.println("Timeout, retransmitting EOT.");
            }

        }

        // Calc + Print total transfer time
        long endTime = System.currentTimeMillis();
        double totalTimeSec = (endTime - startTime) / 1000.0;
        System.out.printf("Total transfer time: %.2f seconds%n", totalTimeSec);

        // Close
        fileHandle.close();
        mySocket.close();
    }
}