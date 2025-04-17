import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class JavaMessenger {
    private static final int PORT = 9876;
    private JFrame frame;
    private JTextArea messageArea;
    private JTextField inputField;
    private JButton sendButton;
    private ServerSocket serverSocket;
    private String selectedIP;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new JavaMessenger().createAndShowGUI();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Hiba történt: " + e.getMessage(), 
                                             "Hiba", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    public JavaMessenger() throws IOException {
        // Szerver socket létrehozása
        serverSocket = new ServerSocket(PORT);
        
        // IP címek lekérése
        List<String> ipAddresses = getAllLocalIPs();
        if (!ipAddresses.isEmpty()) {
            selectedIP = ipAddresses.get(0); // Alapértelmezetten az első IP
        } else {
            selectedIP = "127.0.0.1"; // Fallback localhost
        }
        
        // Szerver szál indítása
        new Thread(() -> {
            try {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new ClientHandler(clientSocket).start();
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
    
    // Összes helyi IP cím lekérése
    private List<String> getAllLocalIPs() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                // Kihagyjuk a leállított vagy loopback interfészeket
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                
                // A VPN interfészek általában "tun" vagy "tap" vagy "hamachi" névvel kezdődnek
                String interfaceName = networkInterface.getName().toLowerCase();
                boolean isVPN = interfaceName.contains("tun") || 
                               interfaceName.contains("tap") || 
                               interfaceName.contains("hamachi") ||
                               interfaceName.contains("vpn");
                
                // IP címek begyűjtése
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress addr = inetAddresses.nextElement();
                    // Csak IPv4 címeket veszünk figyelembe és kihagyjuk a VPN címeket, ha azonosíthatók
                    if (addr instanceof Inet4Address && (!isVPN || interfaceName.contains("eth") || interfaceName.contains("wlan"))) {
                        addresses.add(addr.getHostAddress() + " (" + networkInterface.getDisplayName() + ")");
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return addresses;
    }
    
    private void createAndShowGUI() {
        // GUI elemek létrehozása
        frame = new JFrame("Java Messenger");
        messageArea = new JTextArea(20, 50);
        messageArea.setEditable(false);
        inputField = new JTextField(40);
        sendButton = new JButton("Küldés");
        
        // IP címek lekérése GUI számára
        List<String> localIPs = getAllLocalIPs();
        
        // IP választó legördülő menü
        JPanel ipPanel = new JPanel();
        JLabel myIpLabel = new JLabel("Saját IP cím:");
        JComboBox<String> ipSelector = new JComboBox<>();
        localIPs.forEach(ipSelector::addItem);
        if (localIPs.isEmpty()) {
            ipSelector.addItem("127.0.0.1 (localhost)");
        }
        
        ipSelector.addActionListener(e -> {
            String selected = (String) ipSelector.getSelectedItem();
            if (selected != null) {
                selectedIP = selected.split(" ")[0]; // Csak az IP részt vesszük figyelembe
                messageArea.append("Kiválasztott IP cím: " + selectedIP + "\n");
            }
        });
        
        ipPanel.add(myIpLabel);
        ipPanel.add(ipSelector);
        
        // Cél IP panel
        JPanel inputPanel = new JPanel();
        JLabel targetIpLabel = new JLabel("Cél IP cím:");
        JTextField ipField = new JTextField(15);
        ipField.setText("127.0.0.1"); // alapértelmezett localhost
        
        inputPanel.add(targetIpLabel);
        inputPanel.add(ipField);
        inputPanel.add(inputField);
        inputPanel.add(sendButton);
        
        // Panel elrendezés
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(ipPanel, BorderLayout.NORTH);
        
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.add(inputPanel, BorderLayout.SOUTH);
        
        // Eseménykezelők beállítása
        ActionListener sendAction = e -> {
            String ipAddress = ipField.getText().trim();
            String message = inputField.getText().trim();
            
            if (!ipAddress.isEmpty() && !message.isEmpty()) {
                new Thread(() -> sendMessage(ipAddress, message)).start();
                inputField.setText("");
                messageArea.append("Én: " + message + "\n");
            }
        };
        
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);
        
        // Frame beállítások
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null); // Középre helyezés
        frame.setVisible(true);
        
        // Saját IP cím és port megjelenítése
        messageArea.append("A program a következő porton fut: " + PORT + "\n");
        messageArea.append("Válaszd ki a használni kívánt IP címet a legördülő menüből!\n");
        messageArea.append("Összes elérhető IP cím:\n");
        for (String ip : localIPs) {
            messageArea.append("- " + ip + "\n");
        }
        
        // Bezáráskor takarítás
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
    
    private void sendMessage(String ipAddress, String message) {
        try (Socket socket = new Socket(ipAddress, PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            out.println(message);
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                messageArea.append("Hiba az üzenet küldésekor: " + e.getMessage() + "\n");
            });
        }
    }
    
    // Bejövő üzenetek kezelése
    private class ClientHandler extends Thread {
        private Socket clientSocket;
        
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }
        
        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                String message;
                final String senderIP = clientSocket.getInetAddress().getHostAddress();
                
                while ((message = in.readLine()) != null) {
                    final String finalMessage = message;
                    SwingUtilities.invokeLater(() -> {
                        // Értesítés az üzenet érkezéséről
                        messageArea.append(senderIP + ": " + finalMessage + "\n");
                        frame.toFront();
                        frame.repaint();
                        
                        // Opcionálisan felugró üzenet mutatása
                        JOptionPane.showMessageDialog(frame, finalMessage, 
                                                     "Üzenet a következőtől: " + senderIP, 
                                                     JOptionPane.INFORMATION_MESSAGE);
                    });
                }
            } catch (IOException e) {
                // Kezelés csak akkor, ha nem a bezárás okozta
                if (!clientSocket.isClosed()) {
                    e.printStackTrace();
                }
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}