import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FTLAutosaveManager extends JFrame {
    private static final long serialVersionUID = 1L;

    private JSpinner intervalSpinner;
    private JButton playButton;
    private JButton restartButton;
    private JButton restoreButton;
    private JButton exitButton;

    private final File autosaveFolder;
    private final File backupFolder;
    private final File ftlFolder;
    private final File autosaveFile;

    private final Timer backupTimer = new Timer(true);

    public FTLAutosaveManager() {
        setTitle("FTL Autosave Manager");
        setSize(640, 360);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set a custom icon if available
        try {
            setIconImage(new ImageIcon(resourcePath("bg.ico")).getImage());
        } catch (Exception e) {
            System.out.println("Icon not found.");
        }

        // Create a JLayeredPane and set it as the content pane
        JLayeredPane layeredPane = new JLayeredPane();
        setContentPane(layeredPane);

        // Background image setup
        JLabel backgroundLabel = new JLabel();
        backgroundLabel.setBounds(0, 0, getWidth(), getHeight());
        try {
            File file = new File("bg_small.jpg"); // Root folder of the project
            BufferedImage bgImage = ImageIO.read(file);
            backgroundLabel.setIcon(new ImageIcon(bgImage));
        } catch (IOException e) {
            e.printStackTrace();
        }
        layeredPane.add(backgroundLabel, JLayeredPane.DEFAULT_LAYER);

        // Create the control panel with buttons
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel intervalLabel = new JLabel("Autosave Interval:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        controlPanel.add(intervalLabel, gbc);

        intervalSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        intervalSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateSpinnerSuffix();
            }
        });
        gbc.gridx = 1;
        gbc.gridy = 0;
        controlPanel.add(intervalSpinner, gbc);

        playButton = new JButton("Play");
        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                play();
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 1;
        controlPanel.add(playButton, gbc);

        restartButton = new JButton("Restart");
        restartButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                restart();
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 2;
        controlPanel.add(restartButton, gbc);

        restoreButton = new JButton("Restore Backup");
        restoreButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                restoreBackup();
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 3;
        controlPanel.add(restoreButton, gbc);

        exitButton = new JButton("Exit");
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 4;
        controlPanel.add(exitButton, gbc);

        controlPanel.setOpaque(false); // Make sure the panel is transparent
        controlPanel.setBounds(400, 0, 200, 400); // Adjust size and position as needed
        layeredPane.add(controlPanel, JLayeredPane.PALETTE_LAYER); // Ensure the controlPanel is on top

        updateButtonStates();

        // Define folder paths
        String userHome = System.getProperty("user.home");
        ftlFolder = new File(userHome, "Documents/My Games/FasterThanLight");
        autosaveFolder = new File(userHome, "Documents/My Games/autosave");
        backupFolder = new File(userHome, "Documents/My Games/backup");
        autosaveFile = new File(userHome, "AppData/Roaming/FTLautosave.json");

        ensureFoldersExist();

        // Center the window
        centerWindow();
    }

    private void centerWindow() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Dimension screenSize = gc.getBounds().getSize();
        int x = (screenSize.width - getWidth()) / 2;
        int y = (screenSize.height - getHeight()) / 2;
        setLocation(x, y);
    }

    private String getShortcutPath() {
        if (autosaveFile.exists()) {
            try {
                JsonObject json = JsonParser.parseReader(new FileReader(autosaveFile)).getAsJsonObject();
                String shortcutPath = json.get("shortcut_path").getAsString();
                if (new File(shortcutPath).exists()) {
                    return shortcutPath;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select FTL Shortcut");
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String shortcutPath = fileChooser.getSelectedFile().getAbsolutePath();
            try (FileWriter writer = new FileWriter(autosaveFile)) {
                JsonObject json = new JsonObject();
                json.addProperty("shortcut_path", shortcutPath);
                writer.write(json.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return shortcutPath;
        }
        return null;
    }

    private void ensureFoldersExist() {
        if (!autosaveFolder.exists()) {
            autosaveFolder.mkdirs();
        }
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
    }

    private void updateSpinnerSuffix() {
        int value = (int) intervalSpinner.getValue();
        JSpinner.DefaultEditor editor = (JSpinner.DefaultEditor) intervalSpinner.getEditor();
        editor.getTextField().setColumns(value > 1 ? 7 : 6); // Adjust column size
    }

    private void updateButtonStates() {
        restartButton.setEnabled(checkContinueSav(autosaveFolder));
        restoreButton.setEnabled(checkContinueSav(backupFolder));
    }

    private boolean checkContinueSav(File folderPath) {
        return new File(folderPath, "continue.sav").isFile();
    }

    private void play() {
        String shortcutPath = getShortcutPath();
        if (shortcutPath == null) {
            JOptionPane.showMessageDialog(this, "FTL shortcut not found or not selected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            if (!checkContinueSav(autosaveFolder)) {
                cleanAndCopy(ftlFolder, autosaveFolder);
            }
            if (!checkContinueSav(backupFolder)) {
                cleanAndCopy(ftlFolder, backupFolder);
            }

            int intervalMinutes = (int) intervalSpinner.getValue();
            backupTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        copyFolder(autosaveFolder, backupFolder);
                        copyFolder(ftlFolder, autosaveFolder);
                        System.out.println("Backup and copy operation completed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, 0, intervalMinutes * 60 * 1000);

            Runtime.getRuntime().exec(shortcutPath);
            System.out.println("FTL has been launched successfully.");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to run FTL. Reason: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restart() {
        try {
            deleteFolder(ftlFolder);
            copyFolder(autosaveFolder, ftlFolder);
            System.out.println("Restart operation completed successfully.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to copy autosave to FTL folder. Reason: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restoreBackup() {
        try {
            deleteFolder(autosaveFolder);
            copyFolder(backupFolder, autosaveFolder);
            System.out.println("Restore backup operation completed successfully.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to copy backup to autosave folder. Reason: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cleanAndCopy(File sourceFolder, File targetFolder) {
        try {
            deleteFolder(targetFolder);
            copyFolder(sourceFolder, targetFolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteFolder(File folder) throws IOException {
        Files.walk(folder.toPath())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private void copyFolder(File source, File target) throws IOException {
        Files.walk(source.toPath())
                .forEach(sourcePath -> {
                    Path targetPath = target.toPath().resolve(source.toPath().relativize(sourcePath));
                    try {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    private String resourcePath(String filename) {
        return new File(System.getProperty("user.home"), filename).getAbsolutePath();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FTLAutosaveManager frame = new FTLAutosaveManager();
            frame.setVisible(true);
        });
    }
}
