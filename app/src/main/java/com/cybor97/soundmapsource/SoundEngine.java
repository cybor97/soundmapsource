package com.cybor97.soundmapsource;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collections;

/**
 * Universal Sound engine for Android
 * Call init before usage!
 * Fully synchronous/single-threaded, do NOT use in main(UI) thread!
 *
 * @author cybor97
 */
class SoundEngine {
    private static SoundEngine instance;

    static SoundEngine getInstance() {
        if (instance == null)
            instance = new SoundEngine();

        return instance;
    }

    private AudioRecord audioRecord;
    private Config config = new Config();
    private boolean stopServer = false;

    public SoundEngine init(Config config) {
        this.config = config != null ? config : new Config();
        initAudioRecorder(this.config.sampleRate, this.config.channelConfigIn, this.config.audioFormat, this.config.minBufferSize);
        return this;
    }

    public void startTransferAsServer(String bindHost, int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port, 0, InetAddress.getByName(bindHost));
        while (!Thread.interrupted() && !stopServer) {
            final Socket clientSocket = serverSocket.accept();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startTransfer(clientSocket);
                    } catch (IOException e) {
                        //Turn I/O Exceptions off
                    }
                }
            }).start();
        }
        audioRecord.stop();
    }

    public Socket findLocalServer() throws IOException {
        String ipAddress = getIPAddress();
        String ipAddressParts[] = ipAddress.split("\\.");

        String baseAddress = String.format("%1s.%2s.%3s", (Object[]) ipAddressParts);

        for (int i = 0; i < 3; i++) {
            for (int lastBlock = 1; lastBlock <= 255; lastBlock++) {
                String address = baseAddress + lastBlock;
                Socket serverConnection = initServerConnection(address, 12001, 50 * (i + 1));
                if (serverConnection.isConnected()) {
                    return serverConnection;
                }
            }
        }
        return null;
    }

    public void startTransfer(final Socket socket) throws IOException {
        byte[] buffer = new byte[config.minBufferSize];

        OutputStream outputStream = socket.getOutputStream();
        //Tell server minBufferSize
        outputStream.write(ByteBuffer.allocate(4).putInt(config.minBufferSize).array());

        this.audioRecord.startRecording();
        while (!Thread.interrupted()) {
            int readLength = this.audioRecord.read(buffer, 0, buffer.length);
            //TODO: Deal with commands from server side(inputStream)
            outputStream.write(buffer, 0, readLength);
        }
        this.audioRecord.stop();
    }

    public void startLocalRePlayback() {
        int minBufferSize = config.minBufferSize;
        int channelConfigOut = config.channelConfigOut;
        int sampleRate = config.sampleRate;
        int audioFormat = config.audioFormat;

        byte[] buffer = new byte[minBufferSize];

        AudioTrack player = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfigOut,
                audioFormat,
                minBufferSize,
                AudioTrack.MODE_STREAM);

        this.audioRecord.startRecording();

        while (!Thread.interrupted()) {
            this.audioRecord.read(buffer, 0, buffer.length);
            player.write(buffer, 0, buffer.length);

            if (player.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                player.play();
            }
        }

        this.audioRecord.stop();
        this.audioRecord.release();
        player.stop();
        player.release();
    }

    private AudioRecord initAudioRecorder(int sampleRate, int channelConfig, int audioFormat, int minBufferSize) {
        if (this.audioRecord != null) {
            if (this.audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING
                    || this.audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                this.audioRecord.stop();
                this.audioRecord.release();
            }
        }
        this.audioRecord = new AudioRecordStream(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize);
        return this.audioRecord;
    }

    private Socket initServerConnection(String hostname, int port, int timeout) throws IOException {
        Socket clientSocket = new Socket();
        clientSocket.setKeepAlive(true);
        clientSocket.setTcpNoDelay(true);

        clientSocket.connect(new InetSocketAddress(hostname, port), timeout);
        return clientSocket;
    }

    private String getIPAddress() throws IOException {
        for (NetworkInterface netInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress address : Collections.list(netInterface.getInetAddresses())) {
                if (!address.isLoopbackAddress()) {
                    //Is IPv4
                    String hostname = address.getHostAddress();
                    if (hostname.indexOf(':') < 0)
                        return hostname;
                }
            }
        }
        throw new IOException("Device IP address is not found!");
    }

    private SoundEngine() {
    }

    public static class Config {
        private int sampleRate = 8000;
        private int channelConfigIn = AudioFormat.CHANNEL_IN_MONO;
        private int channelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
        private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        private int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat);

        /**
         * Initializes SoundEngine Config with defaults
         * sampleRate       = 8000Hz
         * channelConfig    = {@link AudioFormat}.CHANNEL_IN_MONO
         * audioFormat      = {@link AudioFormat}.ENCODING_PCM_16BIT
         * minBufferSize    = {@link AudioRecord}.getMinBufferSize(...)
         */
        public Config() {
        }

        /**
         * Initializes SoundEngine Config
         *
         * @param sampleRate       Sample rate
         * @param channelConfigIn  IN channel config
         * @param channelConfigOut OUT channel config. WARNING: Should match IN.
         * @param audioFormat      Used audio format. WARNING: Tested/works only with ENCODING_PCM_16BIT!
         * @param minBufferSize    Buffer size, treated as minimal
         */
        public Config(int sampleRate, int channelConfigIn, int channelConfigOut, int audioFormat, int minBufferSize) {
            this.sampleRate = sampleRate;
            this.channelConfigIn = channelConfigIn;
            this.channelConfigOut = channelConfigOut;
            this.audioFormat = audioFormat;
            this.minBufferSize = minBufferSize;
        }
    }
}
