package com.akchi;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.InputStream;

public class SoundPlayer {
    public static void playSound(String soundFileName) {
        try (InputStream audioSrc = SoundPlayer.class.getResourceAsStream("/" + soundFileName)) {
            InputStream bufferedIn = new BufferedInputStream(audioSrc);
            System.out.println("aaaa");
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            clip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
