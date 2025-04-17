import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import javax.swing.filechooser.FileNameExtensionFilter;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

public class JavaMessengerWithVideo {
    private static final int PORT = 9876;
    private JFrame frame;
    private JTextArea messageArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton sendImageButton;
    private JButton sendVideoButton;
    private ServerSocket serverSocket;
    private String selectedIP;
    
    // JavaFX inicializálás
    static {
        // JavaFX inicializálása
        Platform.setImplicitExit(false);
    }
    
    public static void main(String[] args) {
        // Inicializáljuk a JavaFX platformot
        new JFXPanel(); // Ez inicializálja a JavaFX környezetet
        
        SwingUtilities.invokeLater(() -> {
            try {
                new JavaMessengerWithVideo().createAndShowGUI();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Hiba történt: " + e.getMessage(), 
                                             "Hiba", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
    
    public JavaMessengerWithVideo() throws IOException {
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
        frame = new JFrame("Java Messenger with Video");
        messageArea = new JTextArea(20, 50);
        messageArea.setEditable(false);
        inputField = new JTextField(40);
        sendButton = new JButton("Küldés");
        sendImageButton = new JButton("Kép küldése");
        sendVideoButton = new JButton("Videó küldése");
        
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
        inputPanel.add(sendVideoButton);
        
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
                    new Thread(() -> sendFile(ipAddress, selectedFile, "IMAGE")).start();
                    messageArea.append("Én: [KÉP: " + selectedFile.getName() + "]\n");
                }
            }
        });
        
        // Videó küldés eseménykezelő
        sendVideoButton.addActionListener(e -> {
            String ipAddress = ipField.getText().trim();
            if (!ipAddress.isEmpty()) {
                JFileChooser fileChooser = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    "Videófájlok", "mp4", "avi", "mov", "wmv", "flv", "mkv");
                fileChooser.setFileFilter(filter);
                
                int result = fileChooser.showOpenDialog(frame);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    new Thread(() -> sendFile(ipAddress, selectedFile, "VIDEO")).start();
                    messageArea.append("Én: [VIDEÓ: " + selectedFile.getName() + "]\n");
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
                Platform.exit(); // Leállítjuk a JavaFX platformot
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
    
    // Általános fájl küldés (kép vagy videó)
    private void sendFile(String ipAddress, File file, String fileType) {
        try (Socket socket = new Socket(ipAddress, PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             FileInputStream fileIn = new FileInputStream(file)) {
            
            // Jelezzük a fájl típusát (kép vagy videó)
            out.writeUTF(fileType);
            
            // Fájlnév küldése
            out.writeUTF(file.getName());
            
            // Fájlméret küldése
            long fileSize = file.length();
            out.writeLong(fileSize);
            
            // Fájl tartalmának küldése
            byte[] buffer = new byte[8192]; // Nagyobb buffer a videókhoz
            int bytesRead;
            long totalSent = 0;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
                
                // Előrehaladás kijelzése
                final int percentComplete = (int)((totalSent * 100) / fileSize);
                final String fileTypeStr = fileType.equals("IMAGE") ? "Kép" : "Videó";
                SwingUtilities.invokeLater(() -> {
                    messageArea.append("\r" + fileTypeStr + " küldése: " + percentComplete + "% kész");
                });
            }
            out.flush();
            
            final String fileTypeStr = fileType.equals("IMAGE") ? "Kép" : "Videó";
            SwingUtilities.invokeLater(() -> {
                messageArea.append("\n" + fileTypeStr + " sikeresen elküldve: " + file.getName() + "\n");
            });
        } catch (IOException e) {
            final String fileTypeStr = fileType.equals("IMAGE") ? "kép" : "videó";
            SwingUtilities.invokeLater(() -> {
                messageArea.append("Hiba a " + fileTypeStr + " küldésekor: " + e.getMessage() + "\n");
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
                else if ("IMAGE".equals(messageType) || "VIDEO".equals(messageType)) {
                    // Fájl üzenet feldolgozása
                    String fileName = in.readUTF();
                    long fileSize = in.readLong();
                    
                    // Ideiglenes fájl létrehozása
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    File receivedFile = new File(tempDir, "received_" + fileName);
                    
                    // Fájl adatainak fogadása és mentése
                    try (FileOutputStream fileOut = new FileOutputStream(receivedFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        long totalReceived = 0;
                        
                        while (totalReceived < fileSize && (bytesRead = in.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalReceived))) != -1) {
                            fileOut.write(buffer, 0, bytesRead);
                            totalReceived += bytesRead;
                            
                            // Előrehaladás kijelzése
                            final int percentComplete = (int)((totalReceived * 100) / fileSize);
                            final String fileTypeStr = messageType.equals("IMAGE") ? "Kép" : "Videó";
                            SwingUtilities.invokeLater(() -> {
                                messageArea.append("\r" + fileTypeStr + " fogadása: " + percentComplete + "% kész");
                            });
                        }
                        fileOut.flush();
                    }
                    
                    final String fileTypeStr = messageType.equals("IMAGE") ? "Kép" : "Videó";
                    SwingUtilities.invokeLater(() -> {
                        messageArea.append("\n" + fileTypeStr + " fogadva a következőtől: " + senderIP + " [" + fileName + "]\n");
                        frame.toFront();
                        frame.repaint();
                        
                        if ("IMAGE".equals(messageType)) {
                            displayImageWithSaveOption(receivedFile, fileName, senderIP);
                        } else {
                            displayVideoWithSaveOption(receivedFile, fileName, senderIP);
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
        
        // Egyedi képnézegető ablak mentés gombbal
        private void displayImageWithSaveOption(File imageFile, String fileName, String senderIP) {
            try {
                // Kép betöltése
                BufferedImage img = ImageIO.read(imageFile);
                if (img == null) {
                    throw new IOException("Nem sikerült betölteni a képet");
                }
                
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
                
                // Új dialógus ablak létrehozása
                JDialog imageDialog = new JDialog(frame, "Kép a következőtől: " + senderIP, true);
                imageDialog.setLayout(new BorderLayout());
                
                // Kép megjelenítése
                JLabel imageLabel = new JLabel(icon);
                imageLabel.setHorizontalAlignment(JLabel.CENTER);
                
                // Gomb panel létrehozása
                JPanel buttonPanel = new JPanel();
                JButton saveButton = new JButton("Mentés");
                JButton closeButton = new JButton("Bezárás");
                
                // Mentés gomb eseménykezelője
                saveButton.addActionListener(e -> {
                    saveFile(imageFile, fileName, imageDialog);
                });
                
                // Bezárás gomb eseménykezelője
                closeButton.addActionListener(e -> imageDialog.dispose());
                
                // Gombok hozzáadása a panelhez
                buttonPanel.add(saveButton);
                buttonPanel.add(closeButton);
                
                // Komponensek hozzáadása a dialógushoz
                imageDialog.add(new JScrollPane(imageLabel), BorderLayout.CENTER);
                imageDialog.add(buttonPanel, BorderLayout.SOUTH);
                
                // Dialógus megjelenítése
                imageDialog.pack();
                imageDialog.setLocationRelativeTo(frame);
                imageDialog.setVisible(true);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, 
                                         "Hiba a kép megjelenítésekor: " + e.getMessage(),
                                         "Kép hiba", 
                                         JOptionPane.ERROR_MESSAGE);
            }
        }
        
        // Videó lejátszó ablak mentés gombbal
        private void displayVideoWithSaveOption(File videoFile, String fileName, String senderIP) {
            try {
                // Új dialógus ablak létrehozása
                JDialog videoDialog = new JDialog(frame, "Videó a következőtől: " + senderIP, true);
                videoDialog.setLayout(new BorderLayout());
                videoDialog.setSize(800, 600);
                
                // JavaFX panel a videó lejátszáshoz
                JFXPanel videoPanel = new JFXPanel();
                
                // Gomb panel létrehozása
                JPanel buttonPanel = new JPanel();
                JButton saveButton = new JButton("Mentés");
                JButton closeButton = new JButton("Bezárás");
                
                // Mentés gomb eseménykezelője
                saveButton.addActionListener(e -> {
                    saveFile(videoFile, fileName, videoDialog);
                });
                
                // Bezárás gomb eseménykezelője
                closeButton.addActionListener(e -> videoDialog.dispose());
                
                // Gombok hozzáadása a panelhez
                buttonPanel.add(saveButton);
                buttonPanel.add(closeButton);
                
                // Komponensek hozzáadása a dialógushoz
                videoDialog.add(videoPanel, BorderLayout.CENTER);
                videoDialog.add(buttonPanel, BorderLayout.SOUTH);
                
                // Videó betöltése és lejátszása JavaFX szálon
                Platform.runLater(() -> {
                    try {
                        StackPane root = new StackPane();
                        Scene scene = new Scene(root);
                        
                        // Videó fájl betöltése
                        String videoPath = videoFile.toURI().toString();
                        Media media = new Media(videoPath);
                        MediaPlayer mediaPlayer = new MediaPlayer(media);
                        MediaView mediaView = new MediaView(mediaPlayer);
                        
                        // Videó méretének beállítása
                        mediaView.setFitWidth(780);
                        mediaView.setPreserveRatio(true);
                        
                        // Automatikus lejátszás
                        mediaPlayer.setAutoPlay(true);
                        
                        // JavaFX elemek hozzáadása
                        root.getChildren().add(mediaView);
                        videoPanel.setScene(scene);
                        
                        // Ablak bezárásakor leállítjuk a lejátszást
                        videoDialog.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosing(WindowEvent e) {
                                Platform.runLater(() -> {
                                    mediaPlayer.stop();
                                    mediaPlayer.dispose();
                                });
                            }
                        });
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(videoDialog, 
                                                     "Hiba a videó lejátszásakor: " + e.getMessage(),
                                                     "Videó hiba", 
                                                     JOptionPane.ERROR_MESSAGE);
                            videoDialog.dispose();
                        });
                    }
                });
                
                // Dialógus megjelenítése
                videoDialog.setLocationRelativeTo(frame);
                videoDialog.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, 
                                         "Hiba a videó megjelenítésekor: " + e.getMessage(),
                                         "Videó hiba", 
                                         JOptionPane.ERROR_MESSAGE);
            }
        }
        
        // Általános fájlmentő függvény
        private void saveFile(File sourceFile, String fileName, Component parent) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(fileName));
            
            int result = fileChooser.showSaveDialog(parent);
            if (result == JFileChooser.APPROVE_OPTION) {
                File targetFile = fileChooser.getSelectedFile();
                
                // Ha a felhasználó nem adott meg kiterjesztést, ellenőrizzük, hogy az eredeti fájlnév tartalmaz-e
                String targetPath = targetFile.getAbsolutePath();
                if (!targetPath.contains(".") && fileName.contains(".")) {
                    String extension = fileName.substring(fileName.lastIndexOf('.'));
                    targetFile = new File(targetPath + extension);
                }
                
                try {
                    // Fájl másolása
                    copyFile(sourceFile, targetFile);
                    messageArea.append("Fájl mentve: " + targetFile.getAbsolutePath() + "\n");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(parent, 
                                             "Hiba a fájl mentésekor: " + ex.getMessage(),
                                             "Mentési hiba", 
                                             JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        // Fájlmásoló segédfüggvény
        private void copyFile(File source, File target) throws IOException {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(target)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }
}