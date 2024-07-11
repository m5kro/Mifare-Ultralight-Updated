package com.samsung.sprc.fileselector;

import java.io.IOException;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Handler;
import android.os.Message;

public class NfcThread extends Thread {
    private static final int ACTION_NONE = 0;
    private static final int ACTION_READ = 1;
    private static final int ACTION_WRITE = 2;

    private Intent intent;
    private int scanAction;
    private String mTextBufferText;
    private Handler mTextBufferHandler, mToastShortHandler, mToastLongHandler, mShowInfoDialogHandler;

    private byte[] readBuffer = new byte[1024]; // maximum theoretical capacity of MIFARE Ultralight
    private byte[] toWriteBuffer = new byte[1024];

    NfcThread(
            Intent intent,
            int scanAction,
            String mTextBufferText,
            Handler mTextBufferHandler, Handler mToastShortHandler, Handler mToastLongHandler, Handler mShowInfoDialogHandler
    ) {
        this.intent = intent;
        this.scanAction = scanAction;
        this.mTextBufferText = mTextBufferText;
        this.mTextBufferHandler = mTextBufferHandler;
        this.mToastShortHandler = mToastShortHandler;
        this.mToastLongHandler = mToastLongHandler;
        this.mShowInfoDialogHandler = mShowInfoDialogHandler;
    }

    public void run() {
        final Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

        if (scanAction == ACTION_NONE) {
            showToastLong("Please select READ or WRITE before scanning tag");
            return;
        }

        final NfcA mfu = NfcA.get(tagFromIntent);

        if (mfu == null) {
            showToastLong("Tag does not support ISO 14443-A!");
            return;
        }

        byte[] ATQA = mfu.getAtqa();

        if (mfu.getSak() != 0x00 || ATQA.length != 2 || ATQA[0] != 0x44 || ATQA[1] != 0x00) {
            showToastLong("Tag is not MIFARE Ultralight® - compatible!");
            return;
        }

        try {
            if (scanAction == ACTION_READ) {
                mfu.connect();
                int pagesToRead = 15; // Adjust according to your requirements
                for (int i = 0; i < pagesToRead; i++) {
                    byte[] pageData = rdPages(mfu, i);
                    if (pageData != null) {
                        System.arraycopy(pageData, 0, readBuffer, i * 4, 4);
                    } else {
                        showToastLong("Error reading page " + i);
                        break;
                    }
                }
                mfu.close();

                StringBuilder content = new StringBuilder();
                for (int i = 0; i < pagesToRead * 4; i += 4) {
                    content.append(ByteArrayToHexString(readBuffer, i, 4)).append(System.getProperty("line.separator"));
                }
                setTextBuffer(content.toString());
                showToastShort("Read " + pagesToRead + " pages");
            } else if (scanAction == ACTION_WRITE) {
                // The write implementation remains the same
                String[] lines = mTextBufferText.split(System.getProperty("line.separator"));
                int errors = 0;

                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].length() != 8) {
                        errors = 1;
                        break;
                    }
                    if (i >= 256) {
                        errors = 1;
                        break;
                    }
                    if (HexStringToByteArray(lines[i], toWriteBuffer, i * 4) != 0) {
                        errors = 1;
                        break;
                    }
                }

                if (errors != 0) {
                    showToastLong("Format error in data field!");
                    return;
                }

                mfu.connect();
                int pagesRead = rdNumPages(mfu, lines.length);

                if (pagesRead < lines.length) {
                    showInfoDialog("Insufficient space on tag! Could not write " + lines.length + " pages on " + pagesRead + "-page tag");
                    mfu.close();
                    return;
                }

                int bufferOffset = 0;
                int writeDiff;

                for (int i = 0; i < lines.length; i++) {
                    writeDiff = 0;

                    for (int j = 0; j < 4; j++) {
                        if (readBuffer[bufferOffset] != toWriteBuffer[bufferOffset]) writeDiff = 1;
                        bufferOffset++;
                    }

                    if (writeDiff != 0) {
                        if (wrPage(mfu, i) != 0) {
                            showInfoDialog("Error writing page " + i + ". All following pages have NOT been written!");
                            mfu.close();
                            return;
                        }
                    }
                }

                showInfoDialog(lines.length + " pages written successfully");
                mfu.close();
            }
        } catch (Exception e) {
            showToastLong("Communication error! Please try again");
        }
    }

    private byte[] rdPages(NfcA mfu, int page) {
        byte[] response = null;
        try {
            response = mfu.transceive(new byte[]{
                    (byte) 0x30,           // READ a page is 4 bytes long
                    (byte) (page & 0x0ff)  // page address
            });
            return response;
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Retry logic
        try {
            mfu.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mfu.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void setTextBuffer(String text) {
        Message msg = new Message();
        msg.obj = text;
        mTextBufferHandler.sendMessage(msg);
    }

    private void showToastShort(String text) {
        Message msg = new Message();
        msg.obj = text;
        mToastShortHandler.sendMessage(msg);
    }

    private void showToastLong(String text) {
        Message msg = new Message();
        msg.obj = text;
        mToastLongHandler.sendMessage(msg);
    }

    private void showInfoDialog(String text) {
        Message msg = new Message();
        msg.obj = text;
        mShowInfoDialogHandler.sendMessage(msg);
    }

    private int rdNumPages(NfcA mfu, int num) {
        int pagesRead = 0;

        while (rdPages(mfu, pagesRead) == 0) {
            pagesRead++;
            if (pagesRead == num || pagesRead == 256) break;
        }
        return pagesRead;
    }

    private byte wrPage(NfcA mfu, int pageOffset) {
        byte[] cmd = {(byte) 0xA2, (byte) pageOffset, 0x00, 0x00, 0x00, 0x00};
        System.arraycopy(toWriteBuffer, pageOffset * 4, cmd, 2, 4);
        try {
            mfu.transceive(cmd);
        } catch (IOException e) {
            return 1;
        }
        return 0;
    }

    private String ByteArrayToHexString(byte[] inarray, int offset, int length) {
        StringBuilder out = new StringBuilder();
        for (int i = offset; i < offset + length; ++i) {
            out.append(String.format("%02X", inarray[i]));
        }
        return out.toString();
    }

    private int HexStringToByteArray(String instring, byte[] outarray, int outoffset) {
        int errors = 0;
        byte[] nibbles = new byte[2];
        for (int i = 0; i < instring.length(); i += 2) {
            nibbles[0] = (byte) instring.charAt(i + 0);
            nibbles[1] = (byte) instring.charAt(i + 1);
            if (notHex(nibbles[0])) errors = 1;
            if (notHex(nibbles[1])) errors = 1;
            outarray[outoffset] = (byte) ((hex2bin(nibbles[0]) << 4) | hex2bin(nibbles[1]));
            outoffset++;
        }
        return errors;
    }

    private boolean notHex(byte inchar) {
        if (inchar >= '0' && inchar <= '9') return false;
        if (inchar >= 'a' && inchar <= 'f') return false;
        if (inchar >= 'A' && inchar <= 'F') return false;
        return true;
    }

    private byte hex2bin(byte inchar) {
        if (inchar > 'Z') inchar -= ' ';
        if (inchar > '9') inchar -= 7;
        inchar &= 0x0f;
        return inchar;
    }
}
