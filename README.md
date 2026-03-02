# A2

## To Run:
In A2/Receiver:
* Compile java files: javac *.java
* Run the Receiver: Receiver 127.0.0.1 8888 9999 output.txt 5 20
* ```java Receiver <sender_ip> <sender_ack_port> <rcv_data_port> <output_file> <RN>```

In A2/Sender:
* Compile java files: javac *.java
* Insert text you'd like to be delivered inside of input.txt
* Run the Sender: java Sender 127.0.0.1 9999 8888 input.txt 500 20
* ```java Sender <rcv_ip> <rcv_data_port> <sender_ack_port> <input_file> <timeout_ms> [window_size]```


