package com.akchi;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class FTLAutosaveManager extends JFrame {
    private static final long serialVersionUID = 1L;

    private final JSpinner intervalSpinner;
    private final JButton playButton;
    private final JButton restartButton;
    private final JButton restoreButton;

    private final String userHome = System.getProperty("user.home");
    private final File autosaveConfigFile = new File(userHome, "AppData/Roaming/FTLAutoSaveManager/autosaveConfig.json");
    private final File ftlFolder = new File(userHome, "Documents/My Games/FasterThanLight");
    private final File autosaveFolder = new File(userHome, "Documents/My Games/autosave");
    private final File backupFolder = new File(userHome, "Documents/My Games/backup");

    private final Timer backupTimer = new Timer(true);

    public FTLAutosaveManager() throws IOException, FontFormatException {
        setTitle("FTL Autosave Manager");
        setUndecorated(true);
        setSize(640, 360);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 30, 30));

        try {
            setIconImage(new ImageIcon(Objects.requireNonNull(getClass().getClassLoader().getResource("icon.png"))).getImage());
        } catch (Exception e) {
            System.out.println("Icon not found.");
        }

        JLayeredPane layeredPane = new JLayeredPane();
        setContentPane(layeredPane);

        JLabel backgroundLabel = new JLabel();
        backgroundLabel.setBounds(0, 0, getWidth(), getHeight());
        try {
            BufferedImage bgImage = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/bg_small.jpg")));
            backgroundLabel.setIcon(new ImageIcon(bgImage));
        } catch (IOException e) {
            e.printStackTrace();
        }
        layeredPane.add(backgroundLabel, JLayeredPane.DEFAULT_LAYER);

        Font customFont = Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(getClass().getResourceAsStream("/C&C Red Alert [INET].ttf"))).deriveFont(18f);
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(customFont);
        } catch (Exception e) {
            e.printStackTrace();
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
        //String suffix = ;
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
                play();
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 2;
        controlPanel.add(playButton, gbc);

        restartButton = new JButton("Restart");
        restartButton.setFont(customFont);
        restartButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
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
                restoreBackup();
            }
        });

        gbc.gridx = 0;
        gbc.gridy = 4;
        controlPanel.add(restoreButton, gbc);

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
        gbc.gridy = 5;
        controlPanel.add(exitButton, gbc);

        controlPanel.setOpaque(false);
        controlPanel.setBounds(440, 0, 200, 350);
        layeredPane.add(controlPanel, JLayeredPane.PALETTE_LAYER);

        styleButton(playButton);
        styleButton(restartButton);
        styleButton(restoreButton);
        styleButton(exitButton);

        updateButtonColors();
        updateButtonStates();
        ensureFoldersExist();

        centerWindow();
    }

    private void saveIntervalToConfig(int interval) {
        JsonObject json = new JsonObject();
        if (autosaveConfigFile.exists()) {
            try (FileReader reader = new FileReader(autosaveConfigFile)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
        json.addProperty("interval", interval);

        try (FileWriter writer = new FileWriter(autosaveConfigFile)) {
            writer.write(json.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int loadIntervalFromConfig() {
        if (autosaveConfigFile.exists()) {
            try (FileReader reader = new FileReader(autosaveConfigFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json != null && json.has("interval") && !json.get("interval").isJsonNull()) {
                    return json.get("interval").getAsInt();
                }
            } catch (IOException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
        return 5;
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
                e.printStackTrace();
            }
        }

        if (json.has("shortcut_path") && !json.get("shortcut_path").isJsonNull()) {
            String shortcutPath = json.get("shortcut_path").getAsString();
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
            e.printStackTrace();
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select FTL Shortcut");
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String shortcutPath = fileChooser.getSelectedFile().getAbsolutePath();
            json.addProperty("shortcut_path", shortcutPath);
            try (FileWriter writer = new FileWriter(autosaveConfigFile)) {
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
        if (!autosaveConfigFile.getParentFile().exists()) {
            System.out.println("Creating directory: " + autosaveConfigFile.getParentFile().getAbsolutePath());
            autosaveConfigFile.getParentFile().mkdirs();
        }

        if (!autosaveConfigFile.exists()) {
            try {
                if (autosaveConfigFile.createNewFile()) {
                    System.out.println("Created file: " + autosaveConfigFile.getAbsolutePath());
                } else {
                    System.out.println("File already exists: " + autosaveConfigFile.getAbsolutePath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateButtonStates() {
        restartButton.setEnabled(checkContinueSav(autosaveFolder));
        restoreButton.setEnabled(checkContinueSav(backupFolder));
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
            e.printStackTrace();
            return null;
        }
    }

    private void play() {
        String shortcutPath = getShortcutPath();
        if (shortcutPath == null) {
            JOptionPane.showMessageDialog(this, "FTL shortcut not found or not selected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            String targetPath = resolveShortcutTarget(shortcutPath);
            if (targetPath == null) {
                JOptionPane.showMessageDialog(this, "Failed to resolve shortcut target.", "Error", JOptionPane.ERROR_MESSAGE);
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
            System.out.println("Backup interval set to " + intervalMinutes + " minutes.");
            backupTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    copyFolder(ftlFolder, autosaveFolder);
                    createBackup();
                    System.out.println("Backup and copy operation completed.");
                    updateButtonStates();
                }
            }, 0, (long) intervalMinutes * 60 * 1000);

            Runtime.getRuntime().exec(targetPath);
            System.out.println("FTL has been launched successfully.");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to run FTL. Reason: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            deleteShortcutPath();
        }
    }

    private void createBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

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
                e.printStackTrace();
            }
        }
        json.remove("shortcut_path");
        try (FileWriter writer = new FileWriter(autosaveConfigFile)) {
            writer.write(json.toString());
        } catch (IOException e) {
            e.printStackTrace();
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

    private void copyFolder(File sourceFolder, File targetFolder) {
        try {
            deleteFolder(targetFolder);
            copyFiles(sourceFolder, targetFolder);
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

    private void copyFiles(File source, File target) throws IOException {
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
