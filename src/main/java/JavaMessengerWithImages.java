import java.awt.BorderLayout;
import java.awt.Image;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

public class JavaMessengerWithImages {
    private static final int PORT = 9876;
    private JFrame frame;
    private JTextArea messageArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton sendImageButton;
    private ServerSocket serverSocket;
    private String selectedIP;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new JavaMessengerWithImages().createAndShowGUI();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Hiba történt: " + e.getMessage(), 
                                             "Hiba", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    public JavaMessengerWithImages() throws IOException {
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
        frame = new JFrame("Java Messenger with Images");
        messageArea = new JTextArea(20, 50);
        messageArea.setEditable(false);
        inputField = new JTextField(40);
        sendButton = new JButton("Küldés");
        sendImageButton = new JButton("Kép küldése");
        
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
        inputPanel.add(sendImageButton);
        
        // Panel elrendezés
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(ipPanel, BorderLayout.NORTH);
        
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.add(inputPanel, BorderLayout.SOUTH);
        
        // Eseménykezelők beállítása - szöveges üzenethez
        ActionListener sendAction = e -> {
            String ipAddress = ipField.getText().trim();
            String message = inputField.getText().trim();
            
            if (!ipAddress.isEmpty() && !message.isEmpty()) {
                new Thread(() -> sendTextMessage(ipAddress, message)).start();
                inputField.setText("");
                messageArea.append("Én: " + message + "\n");
            }
        };
        
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);
        
        // Kép küldés eseménykezelő
        sendImageButton.addActionListener(e -> {
            String ipAddress = ipField.getText().trim();
            if (!ipAddress.isEmpty()) {
                JFileChooser fileChooser = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    "Képfájlok", "jpg", "png", "gif", "bmp", "jpeg");
                fileChooser.setFileFilter(filter);
                
                int result = fileChooser.showOpenDialog(frame);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    new Thread(() -> sendImageFile(ipAddress, selectedFile)).start();
                    messageArea.append("Én: [KÉP: " + selectedFile.getName() + "]\n");
                }
            }
        });
        
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
    
    // Szöveges üzenet küldése
    private void sendTextMessage(String ipAddress, String message) {
        try (Socket socket = new Socket(ipAddress, PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            
            // Jelezzük, hogy szöveges üzenet következik
            out.writeUTF("TEXT");
            // Elküldjük a szöveget
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                messageArea.append("Hiba az üzenet küldésekor: " + e.getMessage() + "\n");
            });
        }
    }
    
    // Kép fájl küldése
    private void sendImageFile(String ipAddress, File imageFile) {
        try (Socket socket = new Socket(ipAddress, PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             FileInputStream fileIn = new FileInputStream(imageFile)) {
            
            // Jelezzük, hogy kép következik
            out.writeUTF("IMAGE");
            
            // Fájlnév küldése
            out.writeUTF(imageFile.getName());
            
            // Fájlméret küldése
            long fileSize = imageFile.length();
            out.writeLong(fileSize);
            
            // Fájl tartalmának küldése
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalSent = 0;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
                
                // Előrehaladás kijelzése
                final int percentComplete = (int)((totalSent * 100) / fileSize);
                SwingUtilities.invokeLater(() -> {
                    messageArea.append("\rKüldés: " + percentComplete + "% kész");
                });
            }
            out.flush();
            
            SwingUtilities.invokeLater(() -> {
                messageArea.append("\nKép sikeresen elküldve: " + imageFile.getName() + "\n");
            });
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                messageArea.append("Hiba a kép küldésekor: " + e.getMessage() + "\n");
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
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream())) {
                // Üzenet típusának meghatározása
                String messageType = in.readUTF();
                final String senderIP = clientSocket.getInetAddress().getHostAddress();
                
                if ("TEXT".equals(messageType)) {
                    // Szöveges üzenet feldolgozása
                    String message = in.readUTF();
                    SwingUtilities.invokeLater(() -> {
                        messageArea.append(senderIP + ": " + message + "\n");
                        frame.toFront();
                        frame.repaint();
                        
                        // Felugró üzenet mutatása
                        JOptionPane.showMessageDialog(frame, message, 
                                                     "Üzenet a következőtől: " + senderIP, 
                                                     JOptionPane.INFORMATION_MESSAGE);
                    });
                } 
                else if ("IMAGE".equals(messageType)) {
                    // Kép üzenet feldolgozása
                    String fileName = in.readUTF();
                    long fileSize = in.readLong();
                    
                    // Ideiglenes fájl létrehozása a képnek
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    File receivedFile = new File(tempDir, "received_" + fileName);
                    
                    // Kép adatainak fogadása és mentése
                    try (FileOutputStream fileOut = new FileOutputStream(receivedFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long totalReceived = 0;
                        
                        while (totalReceived < fileSize && (bytesRead = in.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                            fileOut.write(buffer, 0, bytesRead);
                            totalReceived += bytesRead;
                            
                            // Előrehaladás kijelzése
                            final int percentComplete = (int)((totalReceived * 100) / fileSize);
                            SwingUtilities.invokeLater(() -> {
                                messageArea.append("\rFogadás: " + percentComplete + "% kész");
                            });
                        }
                        fileOut.flush();
                    }
                    
                    // Kép megjelenítése
                    SwingUtilities.invokeLater(() -> {
                        messageArea.append("\nKép fogadva a következőtől: " + senderIP + " [" + fileName + "]\n");
                        frame.toFront();
                        frame.repaint();
                        
                        try {
                            // Kép betöltése
                            BufferedImage img = ImageIO.read(receivedFile);
                            if (img != null) {
                                // Képméret igazítása (max 800x600)
                                int maxWidth = 800;
                                int maxHeight = 600;
                                int width = img.getWidth();
                                int height = img.getHeight();
                                
                                double scale = 1.0;
                                if (width > maxWidth) {
                                    scale = (double)maxWidth / width;
                                }
                                if (height * scale > maxHeight) {
                                    scale = (double)maxHeight / height;
                                }
                                
                                int scaledWidth = (int)(width * scale);
                                int scaledHeight = (int)(height * scale);
                                
                                // Átméretezett kép
                                Image scaledImg = img.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
                                ImageIcon icon = new ImageIcon(scaledImg);
                                
                                // Kép megjelenítése felugró ablakban
                                JOptionPane.showMessageDialog(frame, new JLabel(icon), 
                                                         "Kép a következőtől: " + senderIP,
                                                         JOptionPane.PLAIN_MESSAGE);
                            } else {
                                JOptionPane.showMessageDialog(frame, 
                                                         "Nem sikerült betölteni a képet: " + fileName,
                                                         "Kép hiba", 
                                                         JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (IOException e) {
                            JOptionPane.showMessageDialog(frame, 
                                                     "Hiba a kép megjelenítésekor: " + e.getMessage(),
                                                     "Kép hiba", 
                                                     JOptionPane.ERROR_MESSAGE);
                        }
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