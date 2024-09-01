import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

public class FTLBackupManager extends JFrame {
    private final String userHome = System.getProperty("user.home");
    private final String ftlFolder = userHome + "/Documents/My Games/FasterThanLight";
    private final String autosaveFolder = userHome + "/Documents/My Games/autosave";
    private final String backupFolder = userHome + "/Documents/My Games/backup";
    private ScheduledExecutorService scheduler;

    public FTLBackupManager() {
        ensureFoldersExist();
        initializeUI();
    }

    private void initializeUI() {
        setTitle("FTL Backup Manager");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(4, 1));

        JButton playButton = new JButton("Play FTL");
        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playGame(5);  // Set backup interval to 5 minutes
            }
        });
        add(playButton);

        JButton restoreButton = new JButton("Restore Backup");
        restoreButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                restoreBackupDialog();
            }
        });
        add(restoreButton);

        JButton restartButton = new JButton("Restart Game");
        restartButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cleanAndCopy(autosaveFolder, ftlFolder);
                JOptionPane.showMessageDialog(null, "Game restarted from autosave.");
            }
        });
        add(restartButton);

        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.shutdown();
                }
                System.exit(0);
            }
        });
        add(exitButton);

        setVisible(true);
    }

    private void ensureFoldersExist() {
        new File(autosaveFolder).mkdirs();
        new File(backupFolder).mkdirs();
    }

    private boolean checkContinueSav(String folderPath) {
        return new File(folderPath + "/continue.sav").exists();
    }

    private void cleanAndCopy(String sourceFolder, String targetFolder) {
        try {
            Files.walk(Paths.get(targetFolder))
                .map(Path::toFile)
                .sorted((o1, o2) -> -o1.compareTo(o2))
                .forEach(File::delete);

            Files.walk(Paths.get(sourceFolder))
                .forEach(source -> {
                    Path destination = Paths.get(targetFolder, source.toString().substring(sourceFolder.length()));
                    try {
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void playGame(int intervalMinutes) {
        String shortcutPath = getShortcutPath();
        if (shortcutPath == null) {
            JOptionPane.showMessageDialog(this, "FTL shortcut not found or not selected.");
            return;
        }

        if (!checkContinueSav(autosaveFolder)) {
            cleanAndCopy(ftlFolder, autosaveFolder);
        }

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                backupCycle();
            }
        }, 0, intervalMinutes, TimeUnit.MINUTES);

        try {
            Desktop.getDesktop().open(new File(shortcutPath));
            JOptionPane.showMessageDialog(this, "FTL has been launched successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void backupCycle() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String currentBackupFolder = backupFolder + "/" + timestamp;

        new File(currentBackupFolder).mkdirs();
        cleanAndCopy(autosaveFolder, currentBackupFolder);
        cleanAndCopy(ftlFolder, autosaveFolder);
        System.out.println("Backup created at " + timestamp);
    }

    private String getShortcutPath() {
        String appDataPath = System.getenv("APPDATA");
        File autosaveFile = new File(appDataPath + "/FTLautosave.json");
        if (autosaveFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(autosaveFile.toPath()));
                return new JSONObject(content).getString("shortcut_path");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void restoreBackupDialog() {
        List<String> backups = getBackupFolders();
        if (backups.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No backups available.");
            return;
        }

        String selectedBackup = (String) JOptionPane.showInputDialog(
                this,
                "Select Backup to Restore:",
                "Restore Backup",
                JOptionPane.PLAIN_MESSAGE,
                null,
                backups.toArray(),
                backups.get(0));

        if (selectedBackup != null) {
            restoreBackup(selectedBackup);
        }
    }

    private List<String> getBackupFolders() {
        File folder = new File(backupFolder);
        String[] folders = folder.list((current, name) -> new File(current, name).isDirectory());
        if (folders != null) {
            Arrays.sort(folders);
            return Arrays.asList(folders);
        }
        return Arrays.asList();
    }

    private void restoreBackup(String backupFolderName) {
        String restorePath = backupFolder + "/" + backupFolderName;
        if (!new File(restorePath).exists()) {
            JOptionPane.showMessageDialog(this, "Backup folder " + backupFolderName + " does not exist.");
            return;
        }

        cleanAndCopy(restorePath, ftlFolder);
        JOptionPane.showMessageDialog(this, "Restored backup from " + backupFolderName + ".");
    }
/*
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new FTLBackupManager();
            }
        });
    }

 */
public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new FTLBackupManager());
}


}
