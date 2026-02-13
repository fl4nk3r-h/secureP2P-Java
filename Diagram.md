# SecureP2P UML Diagrams

## 1. Class Diagram

```mermaid
classDiagram
    %% Core Classes
    class Main {
        +main(String[] args)
        -startServer()
        -startClient()
        -startInteractivePeer()
    }

    %% Network Layer
    class Client {
        -Socket socket
        -PrintWriter out
        -BufferedReader in
        -String address
        -int port
        +Client(String address, int port)
        +sendMessage(String message)
        +receiveMessage() String
        +close()
    }

    class Server {
        -ServerSocket serverSocket
        -Socket clientSocket
        -PrintWriter out
        -BufferedReader in
        -int port
        +Server(int port)
        +start()
        +close()
    }

    class AsyncPeer {
        -String peerId
        -int port
        -ServerSocket serverSocket
        -Socket peerSocket
        -PrintWriter out
        -BufferedReader in
        -ExecutorService executor
        -BlockingQueue~String~ messageQueue
        -KeyExchange keyExchange
        -SecretKey sessionKey
        -boolean connectionReady
        +AsyncPeer(String peerId, int port)
        +connectToPeerAsync(String address, int port, Consumer callback)
        +acceptConnectionAsync(Consumer callback)
        +exchangePeerId() String
        +performKeyExchangeAsync(Runnable onComplete)
        +sendMessageAsync(String message)
        +pollMessage() String
        +waitForConnectionReady(long timeoutMillis) boolean
        +close()
        +getPeerId() String
    }

    %% Cryptography Layer
    class CryptoUtils {
        -ALGORITHM: String
        -TRANSFORMATION: String
        +encrypt(String data, SecretKey key) String
        +decrypt(String encryptedData, SecretKey key) String
        +generateKey() SecretKey
        +getKeyFromString(String keyString) SecretKey
    }

    class KeyExchange {
        -KeyPairGenerator keyPairGen
        -KeyPair keyPair
        -KeyAgreement keyAgreement
        +KeyExchange()
        +getPublicKeyString() String
        +generateSharedSecret(String otherPublicKeyString) byte[]
        +getSharedSecretString(String otherPublicKeyString) String
        -deriveAESKey(byte[] sharedSecret) SecretKey
    }

    %% Protocol Layer
    class Message {
        -String messageId
        -String senderId
        -String receiverId
        -MessageType type
        -String content
        -long timestamp
        -String signature
        +Message(String senderId, String receiverId, MessageType type, String content)
        +getMessageId() String
        +getSenderId() String
        +getReceiverId() String
        +getType() MessageType
        +getContent() String
        +getTimestamp() long
        +getSignature() String
        +setSignature(String signature)
    }

    class MessageType {
        <<enumeration>>
        KEY_EXCHANGE
        DATA
        ACK
        DISCONNECT
        HEARTBEAT
        ERROR
        +getValue() String
        +fromValue(String value) MessageType
    }

    %% Relationships
    Main --> Client : uses
    Main --> Server : uses
    Main --> AsyncPeer : uses
    
    AsyncPeer --> KeyExchange : has
    AsyncPeer --> CryptoUtils : uses
    AsyncPeer --> Message : sends/receives
    
    Message --> MessageType : has
    
    KeyExchange --> CryptoUtils : derives key using
    
    Client ..> CryptoUtils : may use
    Server ..> CryptoUtils : may use
```

## 2. Activity Diagram - Secure P2P Communication Flow

```mermaid
flowchart TD
    Start([User Starts SecureP2P]) --> Mode{Select Mode}
    
    Mode -->|Server Mode| InitServer[Initialize Server on Port]
    Mode -->|Client Mode| InitClient[Initialize Client]
    Mode -->|Interactive Peer| InitPeer[Initialize Peer]
    
    %% Server Flow
    InitServer --> ListenServer[Listen for Connections]
    ListenServer --> AcceptConn[Accept Client Connection]
    AcceptConn --> ServerReceive[Receive Message]
    ServerReceive --> ServerEcho[Echo Message Back]
    ServerEcho --> ServerClose[Close Connection]
    ServerClose --> End([End])
    
    %% Client Flow
    InitClient --> ConnectServer[Connect to Server]
    ConnectServer --> SendMsg[Send Message]
    SendMsg --> ReceiveResp[Receive Response]
    ReceiveResp --> ClientClose[Close Connection]
    ClientClose --> End
    
    %% Peer Flow
    InitPeer --> PeerMode{Listen or Connect?}
    
    PeerMode -->|Listen| StartListen[Start Server Socket]
    StartListen --> WaitPeer[Wait for Peer Connection]
    WaitPeer --> PeerConnected[Peer Connected]
    
    PeerMode -->|Connect| ConnectPeer[Connect to Peer]
    ConnectPeer --> PeerConnected
    
    PeerConnected --> ExchangeID[Exchange Peer IDs]
    ExchangeID --> DHKeyExchange[Perform DH Key Exchange]
    DHKeyExchange --> GenPubKey[Generate DH Public Key]
    GenPubKey --> SendPubKey[Send Public Key to Peer]
    SendPubKey --> RecvPubKey[Receive Peer Public Key]
    RecvPubKey --> ComputeSecret[Compute Shared Secret]
    ComputeSecret --> DeriveAES[Derive AES-256 Key via SHA-256]
    DeriveAES --> SecureChannel[Secure Channel Established]
    
    SecureChannel --> ChatLoop{Interactive Chat}
    ChatLoop -->|User Input| EncryptMsg[Encrypt Message with AES]
    EncryptMsg --> SendEncrypted[Send Encrypted Message]
    SendEncrypted --> ChatLoop
    
    ChatLoop -->|Receive Message| RecvEncrypted[Receive Encrypted Message]
    RecvEncrypted --> DecryptMsg[Decrypt Message with AES]
    DecryptMsg --> DisplayMsg[Display Message]
    DisplayMsg --> ChatLoop
    
    ChatLoop -->|Exit Command| ClosePeer[Close Peer Connection]
    ClosePeer --> End
    
    style Start fill:#e1f5e1
    style End fill:#ffe1e1
    style SecureChannel fill:#e1e5ff
    style DeriveAES fill:#fff4e1
```

## 3. Activity Diagram - File Transfer (Future Enhancement)

```mermaid
flowchart TD
    Start([Initiate File Transfer]) --> SelectFile[User Selects File]
    SelectFile --> CheckFile{File Exists?}
    
    CheckFile -->|No| Error1[Display Error]
    Error1 --> End([End])
    
    CheckFile -->|Yes| ChunkFile[Split File into Chunks]
    ChunkFile --> Loop{More Chunks?}
    
    Loop -->|Yes| EncryptChunk[Encrypt Chunk with AES-GCM]
    EncryptChunk --> SendChunk[Send Encrypted Chunk]
    SendChunk --> RecvChunk[Peer Receives Chunk]
    RecvChunk --> VerifyTag{Verify GCM Tag?}
    
    VerifyTag -->|Invalid| SendNack[Send NACK]
    SendNack --> AbortTransfer[Abort Transfer]
    AbortTransfer --> End
    
    VerifyTag -->|Valid| SendAck[Send ACK]
    SendAck --> DecryptChunk[Decrypt Chunk]
    DecryptChunk --> StoreChunk[Store Chunk]
    StoreChunk --> Loop
    
    Loop -->|No| AssembleFile[Reassemble File]
    AssembleFile --> VerifyIntegrity{Verify File Integrity?}
    
    VerifyIntegrity -->|Failed| Error2[Display Error]
    Error2 --> End
    
    VerifyIntegrity -->|Success| SaveFile[Save File to Disk]
    SaveFile --> Success[Display Success Message]
    Success --> End
    
    style Start fill:#e1f5e1
    style End fill:#ffe1e1
    style Success fill:#d4edda
    style AbortTransfer fill:#f8d7da
```

## 4. Statechart Diagram - AsyncPeer State Machine

```mermaid
stateDiagram-v2
    [*] --> Initialized : AsyncPeer Created
    
    Initialized --> Listening : acceptConnectionAsync()
    Initialized --> Connecting : connectToPeerAsync()
    
    Listening --> PeerConnected : Peer Connects
    Connecting --> PeerConnected : Connection Established
    Connecting --> Error : Connection Failed
    
    PeerConnected --> ExchangingIDs : exchangePeerId()
    ExchangingIDs --> KeyExchange : IDs Exchanged
    
    KeyExchange --> GeneratingKeys : Generate DH Keys
    GeneratingKeys --> SendingPublicKey : Send Public Key
    SendingPublicKey --> ReceivingPublicKey : Await Peer Key
    ReceivingPublicKey --> ComputingSecret : Compute Shared Secret
    ComputingSecret --> DerivingAESKey : SHA-256 Derivation
    DerivingAESKey --> SecureChannelReady : AES Key Ready
    
    SecureChannelReady --> Active : Start Communication
    
    Active --> Encrypting : sendMessageAsync()
    Encrypting --> Sending : Encrypt with AES
    Sending --> Active : Message Sent
    
    Active --> Receiving : Message Arrives
    Receiving --> Decrypting : Decrypt with AES
    Decrypting --> Active : Message Decrypted
    
    Active --> Disconnecting : close() or Error
    Disconnecting --> Closed : Cleanup Resources
    
    Error --> Closed : Cleanup Resources
    Closed --> [*]
    
    note right of KeyExchange
        Diffie-Hellman
        Key Exchange Protocol
    end note
    
    note right of DerivingAESKey
        SHA-256 used to derive
        256-bit AES key from
        DH shared secret
    end note
    
    note right of Active
        Secure channel active
        All messages encrypted
        with AES-256
    end note
```

## 5. Statechart Diagram - Message Lifecycle

```mermaid
stateDiagram-v2
    [*] --> Created : New Message
    
    Created --> Pending : Added to Queue
    
    Pending --> Encrypting : dequeue()
    Encrypting --> Encrypted : AES Encryption
    
    Encrypted --> InTransit : Sent over Socket
    
    InTransit --> Received : Arrives at Peer
    Received --> Decrypting : Read from Stream
    Decrypting --> Decrypted : AES Decryption
    
    Decrypted --> Validated : Verify Integrity
    Validated --> Delivered : Display to User
    
    Delivered --> [*]
    
    InTransit --> Lost : Network Error
    Lost --> [*]
    
    Decrypting --> Failed : Decryption Error
    Failed --> [*]
    
    note right of Encrypting
        Uses session AES key
        derived from DH exchange
    end note
```

## 6. Deployment Diagram

```mermaid
flowchart TB
    subgraph "Deployment Environment"
        subgraph "Peer Node 1"
            subgraph "Hardware 1"
                CPU1[x86_64 Processor]
                RAM1[512MB+ RAM]
                NIC1[Network Interface]
            end
            
            subgraph "Operating System 1"
                OS1[Ubuntu 24 Linux / JVM 21]
            end
            
            subgraph "Application Runtime 1"
                JVM1[Java Virtual Machine]
                
                subgraph "SecureP2P Instance 1"
                    Main1[Main.java]
                    Peer1[AsyncPeer]
                    Crypto1[CryptoUtils]
                    KeyEx1[KeyExchange]
                    Client1[Client]
                end
                
                subgraph "Libraries 1"
                    SLF4J1[SLF4J Logger]
                    Logback1[Logback]
                    JUnit1[JUnit 5]
                end
            end
        end
        
        subgraph "Peer Node 2"
            subgraph "Hardware 2"
                CPU2[x86_64 Processor]
                RAM2[512MB+ RAM]
                NIC2[Network Interface]
            end
            
            subgraph "Operating System 2"
                OS2[Ubuntu 24 Linux / JVM 21]
            end
            
            subgraph "Application Runtime 2"
                JVM2[Java Virtual Machine]
                
                subgraph "SecureP2P Instance 2"
                    Main2[Main.java]
                    Peer2[AsyncPeer]
                    Crypto2[CryptoUtils]
                    KeyEx2[KeyExchange]
                    Server2[Server]
                end
                
                subgraph "Libraries 2"
                    SLF4J2[SLF4J Logger]
                    Logback2[Logback]
                    JUnit2[JUnit 5]
                end
            end
        end
        
        subgraph "Network Infrastructure"
            TCP[TCP/IP Network]
            Router[Router/Switch]
        end
    end
    
    %% Connections
    NIC1 <==> Router
    NIC2 <==> Router
    Router <==> TCP
    
    Peer1 -.->|Encrypted P2P Connection| Peer2
    Client1 -.->|TCP Socket| Server2
    
    Main1 --> Peer1
    Main2 --> Peer2
    
    Peer1 --> Crypto1
    Peer1 --> KeyEx1
    Peer2 --> Crypto2
    Peer2 --> KeyEx2
    
    style Peer1 fill:#e1f5e1
    style Peer2 fill:#e1f5e1
    style TCP fill:#fff4e1
    style Crypto1 fill:#ffe1e1
    style Crypto2 fill:#ffe1e1
```

## 7. Component Diagram

```mermaid
flowchart TB
    subgraph "SecureP2P Application"
        subgraph "Presentation Layer"
            CLI[Command Line Interface]
        end
        
        subgraph "Application Layer"
            Main[Main Controller]
            PeerManager[Peer Manager]
        end
        
        subgraph "Network Layer"
            Client[TCP Client]
            Server[TCP Server]
            AsyncPeer[Async Peer]
        end
        
        subgraph "Security Layer"
            CryptoUtils[Crypto Utilities<br/>AES-256 Encryption]
            KeyExchange[Key Exchange<br/>Diffie-Hellman + SHA-256]
        end
        
        subgraph "Protocol Layer"
            Message[Message Protocol]
            MessageType[Message Types]
        end
        
        subgraph "Utility Layer"
            Logger[SLF4J Logger]
            Executor[Thread Executor]
        end
    end
    
    subgraph "External Dependencies"
        JDK[Java JDK 21]
        Network[TCP/IP Stack]
        Crypto[Java Cryptography<br/>Architecture JCA]
    end
    
    %% Internal connections
    CLI --> Main
    Main --> Client
    Main --> Server
    Main --> AsyncPeer
    
    AsyncPeer --> KeyExchange
    AsyncPeer --> CryptoUtils
    AsyncPeer --> Message
    AsyncPeer --> Executor
    
    Client --> Logger
    Server --> Logger
    AsyncPeer --> Logger
    
    Message --> MessageType
    KeyExchange --> CryptoUtils
    
    %% External connections
    CryptoUtils -.->|uses| Crypto
    KeyExchange -.->|uses| Crypto
    Client -.->|uses| Network
    Server -.->|uses| Network
    AsyncPeer -.->|uses| Network
    Main -.->|runs on| JDK
    
    style AsyncPeer fill:#e1f5e1
    style CryptoUtils fill:#ffe1e1
    style KeyExchange fill:#ffe1e1
    style Message fill:#e1e5ff
```

## 8. Sequence Diagram - Complete P2P Session

```mermaid
sequenceDiagram
    actor User1 as User (Peer 1)
    participant Peer1 as AsyncPeer 1
    participant KE1 as KeyExchange 1
    participant Crypto1 as CryptoUtils 1
    participant Network as TCP/IP Network
    participant Crypto2 as CryptoUtils 2
    participant KE2 as KeyExchange 2
    participant Peer2 as AsyncPeer 2
    actor User2 as User (Peer 2)
    
    %% Connection Setup
    User2->>Peer2: Start listening on port 12346
    Peer2->>Network: Open ServerSocket
    activate Peer2
    
    User1->>Peer1: Connect to Peer2 (localhost:12346)
    Peer1->>Network: Establish TCP connection
    Network->>Peer2: Connection request
    Peer2->>Peer2: Accept connection
    
    %% Peer ID Exchange
    Peer1->>Network: Send Peer ID "Alice"
    Network->>Peer2: "Alice"
    Peer2->>Network: Send Peer ID "Bob"
    Network->>Peer1: "Bob"
    
    Note over Peer1,Peer2: Peer IDs Exchanged
    
    %% Key Exchange Phase
    Peer1->>KE1: Generate DH key pair
    activate KE1
    KE1->>KE1: Generate 1024-bit DH keys
    KE1-->>Peer1: Public key
    deactivate KE1
    
    Peer2->>KE2: Generate DH key pair
    activate KE2
    KE2->>KE2: Generate 1024-bit DH keys
    KE2-->>Peer2: Public key
    deactivate KE2
    
    Peer1->>Network: Send DH public key
    Network->>Peer2: DH public key (Base64)
    
    Peer2->>Network: Send DH public key
    Network->>Peer1: DH public key (Base64)
    
    %% Compute Shared Secret
    Peer1->>KE1: generateSharedSecret(Peer2 public key)
    activate KE1
    KE1->>KE1: Compute DH shared secret
    KE1->>KE1: SHA-256(shared secret)
    KE1-->>Peer1: 256-bit AES key
    deactivate KE1
    
    Peer2->>KE2: generateSharedSecret(Peer1 public key)
    activate KE2
    KE2->>KE2: Compute DH shared secret
    KE2->>KE2: SHA-256(shared secret)
    KE2-->>Peer2: 256-bit AES key
    deactivate KE2
    
    Note over Peer1,Peer2: Secure Channel Established<br/>Both peers have same AES-256 key
    
    %% Encrypted Communication
    User1->>Peer1: Type message "Hello Bob!"
    Peer1->>Crypto1: encrypt("Hello Bob!", sessionKey)
    activate Crypto1
    Crypto1->>Crypto1: AES-256 encryption
    Crypto1-->>Peer1: Encrypted message (Base64)
    deactivate Crypto1
    
    Peer1->>Network: Send encrypted message
    Network->>Peer2: Encrypted message
    
    Peer2->>Crypto2: decrypt(encrypted, sessionKey)
    activate Crypto2
    Crypto2->>Crypto2: AES-256 decryption
    Crypto2-->>Peer2: "Hello Bob!"
    deactivate Crypto2
    
    Peer2->>User2: Display "Alice: Hello Bob!"
    
    %% Response
    User2->>Peer2: Type message "Hi Alice!"
    Peer2->>Crypto2: encrypt("Hi Alice!", sessionKey)
    activate Crypto2
    Crypto2-->>Peer2: Encrypted message
    deactivate Crypto2
    
    Peer2->>Network: Send encrypted message
    Network->>Peer1: Encrypted message
    
    Peer1->>Crypto1: decrypt(encrypted, sessionKey)
    activate Crypto1
    Crypto1-->>Peer1: "Hi Alice!"
    deactivate Crypto1
    
    Peer1->>User1: Display "Bob: Hi Alice!"
    
    %% Disconnection
    User1->>Peer1: Exit command
    Peer1->>Network: Close connection
    Network->>Peer2: Connection closed
    Peer2->>Peer2: Cleanup resources
    deactivate Peer2
    
    Note over Peer1,Peer2: Session Terminated
```

## 9. Use Case Diagram

```mermaid
flowchart TD
    subgraph System["SecureP2P System"]
        UC1[Establish Secure Connection]
        UC2[Perform Key Exchange]
        UC3[Send Encrypted Message]
        UC4[Receive Encrypted Message]
        UC5[Verify Message Integrity]
        UC6[Close Connection]
        UC7[Run as Server]
        UC8[Run as Client]
        UC9[Interactive Peer Chat]
    end
    
    User1([Sender Peer])
    User2([Receiver Peer])
    Admin([System Administrator])
    
    User1 --> UC1
    User1 --> UC3
    User1 --> UC9
    
    User2 --> UC1
    User2 --> UC4
    User2 --> UC9
    
    UC1 -.->|includes| UC2
    UC3 -.->|requires| UC2
    UC4 -.->|requires| UC2
    UC4 -.->|includes| UC5
    
    User1 --> UC6
    User2 --> UC6
    
    Admin --> UC7
    Admin --> UC8
    
    UC9 -.->|includes| UC1
    UC9 -.->|includes| UC2
    UC9 -.->|includes| UC3
    UC9 -.->|includes| UC4
    
    style UC1 fill:#e1f5e1
    style UC2 fill:#ffe1e1
    style UC3 fill:#e1e5ff
    style UC4 fill:#e1e5ff
```
