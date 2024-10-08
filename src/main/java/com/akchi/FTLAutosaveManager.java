package com.akchi;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.*;
import java.util.*;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FTLAutosaveManager extends JFrame {
    private static final long serialVersionUID = 1L;

    private final transient Logger logger = Logger.getLogger(getClass().getName());
    private final JSpinner intervalSpinner;
    private final JButton playButton;
    private final JButton restartButton;
    private final JButton restoreButton;
    private final JComboBox<String> backupDropdown;
    private final JButton cancelButton;

    private final String userHome = System.getProperty("user.home");
    private final File autosaveConfigFile = new File(userHome, "AppData/Roaming/FTLAutoSaveManager/autosaveConfig.json");
    private final File ftlFolder = new File(userHome, "Documents/My Games/FasterThanLight");
    private final File autosaveFolder = new File(userHome, "Documents/My Games/autosave");
    private final File backupFolder = new File(userHome, "Documents/My Games/backup");

    private static final String CLICK_SOUND = "click.wav";
    private static final String RESTORE_SOUND = "restore.wav";
    private static final String CANCEL_SOUND = "cancel.wav";
    private static final String INTERVAL_STRING = "interval";
    private static final String SHORTCUT_PATH = "shortcut_path";
    private static final String ERROR_STRING= "Error";

    private final Timer backupTimer = new Timer(true);
    private final Map<String, Clip> soundMap = new HashMap<>();


    public FTLAutosaveManager() throws IOException, FontFormatException {
        preloadSounds();
        setTitle("FTL Autosave Manager");
        setUndecorated(true);
        setSize(640, 360);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 30, 30));

        try {
            setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getClassLoader().getResource("icon.png"))).getImage());
        } catch (Exception e) {
            logger.info("Icon not found.");
        }

        JLayeredPane layeredPane = new JLayeredPane();
        setContentPane(layeredPane);

        JLabel backgroundLabel = new JLabel();
        backgroundLabel.setBounds(0, 0, getWidth(), getHeight());
        try {
            BufferedImage bgImage = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/bg_small.jpg")));
            backgroundLabel.setIcon(new ImageIcon(bgImage));
        } catch (IOException e) {
            logger.info(e.getMessage());
        }
        layeredPane.add(backgroundLabel, JLayeredPane.DEFAULT_LAYER);

        Font customFont = Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(getClass().getResourceAsStream("/C&C Red Alert [INET].ttf"))).deriveFont(18f);
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(customFont);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel intervalLabel = new JLabel("Autosave Interval :");
        intervalLabel.setForeground(Color.WHITE);
        intervalLabel.setFont(customFont);
        intervalLabel.setFont(customFont.deriveFont(20f));
        gbc.gridx = 0;
        gbc.gridy = 0;
        controlPanel.add(intervalLabel, gbc);

        int savedInterval = loadIntervalFromConfig();
        intervalSpinner = new JSpinner(new SpinnerNumberModel(savedInterval, 1, 999, 1));
        intervalSpinner.setFont(customFont);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(intervalSpinner, "# '" + (savedInterval == 1 ? "minute" : "minutes") + "'");
        intervalSpinner.setEditor(editor);

        intervalSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                int value = (Integer) intervalSpinner.getValue();
                editor.getFormat().applyPattern(value == 1 ? "# 'minute'" : "# 'minutes'");
                saveIntervalToConfig(value);
            }
        });
        gbc.gridx = 0;
        gbc.gridy = 1;
        controlPanel.add(intervalSpinner, gbc);

        playButton = new JButton("Play");
        playButton.setFont(customFont);
        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playSound(CLICK_SOUND);
                play();
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 2;
        controlPanel.add(playButton, gbc);

        restartButton = new JButton("Quick restart");
        restartButton.setFont(customFont);
        restartButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playSound(RESTORE_SOUND);
                restart();
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 3;
        controlPanel.add(restartButton, gbc);

        restoreButton = new JButton("Restore Backup");
        restoreButton.setFont(customFont);
        restoreButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playSound(CLICK_SOUND);
                showRestoreUI();
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 4;
        controlPanel.add(restoreButton, gbc);

        backupDropdown = new JComboBox<>();
        backupDropdown.setFont(customFont);
        backupDropdown.setVisible(false);

        gbc.gridx = 0;
        gbc.gridy = 5;
        controlPanel.add(backupDropdown, gbc);

        cancelButton = new JButton("Cancel backup");
        cancelButton.setFont(customFont);
        cancelButton.setVisible(false);
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playSound(CANCEL_SOUND);
                hideRestoreUI();
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 6;
        controlPanel.add(cancelButton, gbc);

        JButton exitButton = new JButton("Exit");
        exitButton.setFont(customFont);
        exitButton.setBackground(Color.DARK_GRAY);
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 7;
        controlPanel.add(exitButton, gbc);

        controlPanel.setOpaque(false);
        controlPanel.setBounds(440, 0, 200, 350);
        layeredPane.add(controlPanel, JLayeredPane.PALETTE_LAYER);

        styleButton(playButton);
        styleButton(restartButton);
        styleButton(restoreButton);
        styleButton(cancelButton);
        styleButton(exitButton);

        backupDropdown.setBackground(Color.DARK_GRAY);
        backupDropdown.setForeground(Color.WHITE);
        backupDropdown.setOpaque(true);

        updateButtonColors();
        updateButtonStates();
        ensureFoldersExist();
        cancelButton.setForeground(new Color(132, 119, 119));
        cancelButton.setBackground(Color.lightGray);

        addClickableImage();
        centerWindow();
    }

    private void preloadSounds() {
        preloadSound(RESTORE_SOUND);
        preloadSound(CLICK_SOUND);
        preloadSound(CANCEL_SOUND);
    }

    private void preloadSound(String soundFileName) {
        try (InputStream audioSrc = getClass().getResourceAsStream("/" + soundFileName);
             InputStream bufferedIn = new BufferedInputStream(audioSrc);
             AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn)) {
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            soundMap.put(soundFileName, clip);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }

    private void playSound(String soundFileName) {
        Clip clip = soundMap.get(soundFileName);
        if (clip != null) {
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.setFramePosition(0);
            clip.start();
        } else {
            logger.log(Level.INFO,"Sound not found: " + soundFileName);
        }
    }



    private void addClickableImage() {
        JLabel linkLabel = new JLabel();
        try {
            ImageIcon linkIcon = new ImageIcon(Objects.requireNonNull(getClass().getClassLoader().getResource("github.png")));
            linkLabel.setIcon(linkIcon);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }

        linkLabel.setBounds(15, getHeight() - 50, 50, 50);
        linkLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        linkLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://github.com/Koussay-Akchi/FTLautosaveManager"));
                } catch (IOException | URISyntaxException eUi) {
                    logger.info(eUi.getMessage());
                }
            }
        });

        JLayeredPane layeredPane = (JLayeredPane) getContentPane();
        layeredPane.add(linkLabel, JLayeredPane.PALETTE_LAYER);
    }

    private void saveIntervalToConfig(int interval) {
        JsonObject json = new JsonObject();
        if (autosaveConfigFile.exists()) {
            try (FileReader reader = new FileReader(autosaveConfigFile)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException | IllegalStateException e) {
                logger.info(e.getMessage());
            }
        }
        json.addProperty(INTERVAL_STRING, interval);

        try (FileWriter writer = new FileWriter(autosaveConfigFile)) {
            writer.write(json.toString());
        } catch (IOException e) {
            logger.info(e.getMessage());
        }
    }

    private int loadIntervalFromConfig() {
        if (autosaveConfigFile.exists()) {
            try (FileReader reader = new FileReader(autosaveConfigFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json != null && json.has(INTERVAL_STRING) && !json.get(INTERVAL_STRING).isJsonNull()) {
                    return json.get(INTERVAL_STRING).getAsInt();
                }
            } catch (IOException | IllegalStateException e) {
                logger.info(e.getMessage());
            }
        }
        return 5;
    }

    private void showRestoreUI() {
        File[] backupFolders = backupFolder.listFiles(File::isDirectory);
        if (backupFolders != null && backupFolders.length > 0) {
            backupDropdown.removeAllItems();
            backupDropdown.addItem("Select the backup");
            Arrays.sort(backupFolders, (a, b) -> b.getName().compareTo(a.getName()));

            for (File folder : backupFolders) {
                backupDropdown.addItem(folder.getName());
            }

            backupDropdown.setVisible(true);
            cancelButton.setVisible(true);
            backupDropdown.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String selectedFolder = (String) backupDropdown.getSelectedItem();
                    if (selectedFolder != null && !"Select the backup".equals(selectedFolder)) {
                        restoreSelectedBackup(selectedFolder);
                        playSound(RESTORE_SOUND);
                        updateButtonStates();
                    }
                }
            });
        } else {
            JOptionPane.showMessageDialog(this, "No backups available.", "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void hideRestoreUI() {
        backupDropdown.setVisible(false);
        cancelButton.setVisible(false);
    }

    private void restoreSelectedBackup(String folderName) {
        File selectedBackupFolder = new File(backupFolder, folderName);
        copyFolder(selectedBackupFolder, ftlFolder);
        logger.log(Level.INFO, "Restored backup from {0}", folderName);
        hideRestoreUI();
    }

    private void styleButton(JButton button) {
        button.setBackground(Color.DARK_GRAY);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setOpaque(true);
    }

    private void updateButtonColors() {
        playButton.setBackground(playButton.isEnabled() ? Color.DARK_GRAY : new Color(40, 40, 40));
        restartButton.setBackground(restartButton.isEnabled() ? Color.DARK_GRAY : new Color(40, 40, 40));
        restoreButton.setBackground(restoreButton.isEnabled() ? Color.DARK_GRAY : new Color(40, 40, 40));
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
        JsonObject json = new JsonObject();
        if (autosaveConfigFile.exists()) {
            try (FileReader reader = new FileReader(autosaveConfigFile)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException | IllegalStateException e) {
                logger.info(e.getMessage());
            }
        }

        if (json.has(SHORTCUT_PATH) && !json.get(SHORTCUT_PATH).isJsonNull()) {
            String shortcutPath = json.get(SHORTCUT_PATH).getAsString();
            if (new File(shortcutPath).exists()) {
                return shortcutPath;
            }
        }

        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Windows".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            logger.info(e.getMessage());
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select FTL Shortcut");
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String shortcutPath = fileChooser.getSelectedFile().getAbsolutePath();
            json.addProperty(SHORTCUT_PATH, shortcutPath);
            try (FileWriter writer = new FileWriter(autosaveConfigFile)) {
                writer.write(json.toString());
            } catch (IOException e) {
                logger.info(e.getMessage());
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
        if (!autosaveConfigFile.getParentFile().exists()) {
            logger.info("Creating directory: " + autosaveConfigFile.getParentFile().getAbsolutePath());
            autosaveConfigFile.getParentFile().mkdirs();
        }

        if (!autosaveConfigFile.exists()) {
            try {
                if (autosaveConfigFile.createNewFile()) {
                    logger.info("Created file: " + autosaveConfigFile.getAbsolutePath());
                } else {
                    logger.info("File already exists: " + autosaveConfigFile.getAbsolutePath());
                }
            } catch (IOException e) {
                logger.info(e.getMessage());
            }
        }
    }

    private void updateButtonStates() {
        restartButton.setEnabled(checkContinueSav(autosaveFolder));
        File[] folders = backupFolder.listFiles(File::isDirectory);
        restoreButton.setEnabled(folders != null && folders.length > 0);
        restartButton.setToolTipText(restartButton.isEnabled() ? null : "Autosave folder is empty");
        restoreButton.setToolTipText(restoreButton.isEnabled() ? null : "Backup folder is empty");
        updateButtonColors();
    }

    private boolean checkContinueSav(File folderPath) {
        return new File(folderPath, "continue.sav").isFile();
    }

    private String resolveShortcutTarget(String shortcutPath) {
        try {
            String command = String.format("powershell -Command \"(New-Object -ComObject WScript.Shell).CreateShortcut('%s').TargetPath\"", shortcutPath);
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String targetPath = reader.readLine().trim();
            reader.close();
            process.waitFor();
            return targetPath;
        } catch (Exception e) {
            logger.info(e.getMessage());
            return null;
        }
    }

    private void play() {
        ensureFoldersExist();
        String shortcutPath = getShortcutPath();
        if (shortcutPath == null) {
            JOptionPane.showMessageDialog(this, "FTL shortcut not found or not selected.", ERROR_STRING, JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            String targetPath = resolveShortcutTarget(shortcutPath);
            if (targetPath == null) {
                JOptionPane.showMessageDialog(this, "Failed to resolve shortcut target.", ERROR_STRING, JOptionPane.ERROR_MESSAGE);
                deleteShortcutPath();
                return;
            }

            if (!checkContinueSav(autosaveFolder)) {
                copyFolder(ftlFolder, autosaveFolder);
            }
            if (!checkContinueSav(backupFolder)) {
                copyFolder(ftlFolder, backupFolder);
            }

            int intervalMinutes = (int) intervalSpinner.getValue();
            logger.log(Level.INFO, "Backup interval set to {0} minutes.", intervalMinutes);
            backupTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    copyFolder(ftlFolder, autosaveFolder);
                    createBackup();
                    logger.info("Backup and copy operation completed.");
                    updateButtonStates();
                }
            }, 0, (long) intervalMinutes * 60 * 1000);

            Runtime.getRuntime().exec(targetPath);
            logger.info("FTL has been launched successfully.");
        } catch (IOException e) {
            logger.info(e.getMessage());
            JOptionPane.showMessageDialog(this, "Failed to run FTL. Reason: " + e.getMessage(), ERROR_STRING, JOptionPane.ERROR_MESSAGE);
            deleteShortcutPath();
        }
    }

    private void createBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd, hh-mm a"));
        File datedBackupFolder = new File(backupFolder, timestamp);

        if (!datedBackupFolder.exists()) {
            datedBackupFolder.mkdirs();
        }

        copyFolder(autosaveFolder, datedBackupFolder);
    }

    private void deleteShortcutPath() {
        JsonObject json = new JsonObject();
        if (autosaveConfigFile.exists()) {
            try (FileReader reader = new FileReader(autosaveConfigFile)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException | IllegalStateException e) {
                logger.info(e.getMessage());
            }
        }
        json.remove(SHORTCUT_PATH);
        try (FileWriter writer = new FileWriter(autosaveConfigFile)) {
            writer.write(json.toString());
        } catch (IOException e) {
            logger.info(e.getMessage());
        }
    }

    private void restart() {
        copyFolder(autosaveFolder, ftlFolder);
        logger.info("Restart operation completed successfully.");
    }

    private void copyFolder(File sourceFolder, File targetFolder) {
        try {
            deleteFolderContents(targetFolder);
            copyFiles(sourceFolder, targetFolder);
        } catch (IOException e) {
            logger.info(e.getMessage());
        }
    }

    private void deleteFolderContents(File folder) throws IOException {
        Files.walk(folder.toPath())
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private void copyFiles(File source, File target) throws IOException {
        Files.walk(source.toPath())
                .forEach(sourcePath -> {
                    Path targetPath = target.toPath().resolve(source.toPath().relativize(sourcePath));
                    try {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        logger.info(e.getMessage());
                    }
                });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FTLAutosaveManager frame = null;
            try {
                frame = new FTLAutosaveManager();
            } catch (IOException | FontFormatException e) {
                throw new RuntimeException(e);
            }
            frame.setVisible(true);
        });
    }
}
