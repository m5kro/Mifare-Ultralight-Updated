package com.samsung.sprc.fileselector;

import java.io.IOException;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Handler;
import android.os.Message;

//import android.util.Log;

public class NfcThread extends Thread {
	private static final int ACTION_NONE  = 0;
	private static final int ACTION_READ  = 1;
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
		
		int pagesRead;
		
		try {
			//Log.i("position", "read data");
			
			if (scanAction == ACTION_READ) {
				mfu.connect();
				pagesRead = rdNumPages(mfu, 0); // 0 for no limit (until error)
				mfu.close();
				
				String content = "";
				byte[] mfuPage = new byte[4];
				for (int i = 0; i < pagesRead * 4; i += 4) {
					System.arraycopy(readBuffer, i, mfuPage, 0,  4);
					content = content + ByteArrayToHexString(mfuPage) + System.getProperty("line.separator");
				}
				setTextBuffer(content);
				showToastShort("Read " + pagesRead + " pages");
			}
			else if (scanAction == ACTION_WRITE) {
				String[] lines = mTextBufferText.split(System.getProperty("line.separator"));
				int errors = 0;
				
				for (int i = 0; i < lines.length; i++) {
					if (lines[i].length() != 8) { errors = 1; break; }
					if (i >= 256)               { errors = 1; break; }
					if (HexStringToByteArray(lines[i], toWriteBuffer, i * 4) != 0) { errors = 1; break; }
				}
				
				if (errors != 0) {
					showToastLong("Format error in data field!");
					return;
				}
				
				mfu.connect();
				pagesRead = rdNumPages(mfu, lines.length);
				
				if (pagesRead < lines.length) {
					showInfoDialog("Insufficient space on tag! Could not write " + lines.length + " pages on " + pagesRead + "-page tag");
					mfu.close();
					return;
				}
				
				int bufferOffset = 0, writeDiff;
				
				for (int i = 0; i < lines.length; i++) {
					writeDiff = 0;
					
					for (int j = 0; j < 4; j++) {
						if(readBuffer[bufferOffset] != toWriteBuffer[bufferOffset]) writeDiff = 1;
						bufferOffset++;
					}
					
					if(writeDiff != 0) {
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
		}
		catch (Exception e) {
			showToastLong("Communication error! Please try again");
		}
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
	
	/*
	private int rdAllPages(NfcA mfu) {
		int pagesRead = 0;
		while (rdPages(mfu, pagesRead) == 0) {
			pagesRead += 4;
			if (pagesRead == 256) break;
		}
		return pagesRead;
	}
	*/
	
	private int rdNumPages(NfcA mfu, int num) {
		int pagesRead = 0;
		
		while (rdPages(mfu, pagesRead) == 0) {
			pagesRead++;
			if (pagesRead == num || pagesRead == 256) break;
		}
		return pagesRead;
	}
	
	/*
	private int rdNumPages(NfcA mfu, int num) {
		int pagesRead = 0;
		
		// align number of pages to a multiple of 4
		num += 3;
		num >>>= 2; // unsigned shift
		num <<= 2;
		
		while (rdPages(mfu, pagesRead) == 0) {
			pagesRead += 4;
			if (pagesRead == num || pagesRead == 256) break;
		}
		return pagesRead;
	}
	*/
	
	// first failure (NAK) causes response 0x00 (or possibly other 1-byte values)
	// second failure (NAK) causes transceive() to throw IOException
	private byte rdPages(NfcA tag, int pageOffset) {
		byte[] cmd = {0x30, (byte)pageOffset};
		byte[] response = new byte[16];
		try { response = tag.transceive(cmd); }
		catch (IOException e) { return 1; }
		if (response.length != 16) return 1;
		//System.arraycopy(response, 0, readBuffer, pageOffset * 4, 16);
		System.arraycopy(response, 0, readBuffer, pageOffset * 4, 4);
		return 0;
	}
	
	// first failure (NAK) causes transceive() to throw IOException
	/*
	private byte wrPage(NfcA tag, int pageOffset) {
		byte[] cmd = {(byte)0xA2, (byte)pageOffset, 0x00, 0x00, 0x00, 0x00};
		System.arraycopy(toWriteBuffer, pageOffset * 4, cmd, 2, 4);
		try {
			Log.i("TRANS START", Integer.toString(pageOffset));
			tag.transceive(cmd);
			Log.i("TRANS END", Integer.toString(pageOffset));
		}
		catch (IOException e) { return 1; }
		return 0;
	}
	*/
	
	private byte wrPage(NfcA mfu, int pageOffset) {
		byte[] cmd = {(byte)0xA2, (byte)pageOffset, 0x00, 0x00, 0x00, 0x00};
		System.arraycopy(toWriteBuffer, pageOffset * 4, cmd, 2, 4);
		//byte[] data = {0x00, 0x00, 0x00, 0x00};
		//System.arraycopy(toWriteBuffer, pageOffset * 4, data, 0, 4);
		//byte errors = 0;
		
		//final MifareUltralight mfu = MifareUltralight.get(tag);
		
		try {
			//mfu.connect();
			//Log.i("TRANS START", Integer.toString(pageOffset));
			//mfu.writePage(pageOffset, data);
			mfu.transceive(cmd);
			//Log.i("TRANS END", Integer.toString(pageOffset));
			//mfu.close();
		}
		catch (final IOException e) { return 1; }
		/*
		finally {
			try { mfu.close(); }
			catch (final Exception e) {}
		}
		*/
		
		return 0;
	}
	
	// first failure (NAK) causes transceive() to throw IOException
	private byte wrPageCompat(NfcA tag, int pageOffset) {
		byte[] cmd1 = {(byte)0xA0, (byte)pageOffset};
		byte[] cmd2 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
		System.arraycopy(toWriteBuffer, pageOffset * 4, cmd2, 0, 4);
		try {
			tag.transceive(cmd1);
			tag.transceive(cmd2);
		}
		catch (IOException e) { return 1; }
		return 0;
	}
	
	private String ByteArrayToHexString(byte[] inarray) {
		int i, j, in;
		String [] hex = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
		String out= "";
		for(j = 0 ; j < inarray.length ; ++j) {
			in = (int) inarray[j] & 0xff;
			i = (in >> 4) & 0x0f;
			out += hex[i];
			i = in & 0x0f;
			out += hex[i];
		}
		return out;
	}
	
	private int HexStringToByteArray(String instring, byte[] outarray, int outoffset) {
		int errors = 0;
		byte[] nibbles = new byte[2];
		for (int i = 0; i < instring.length(); i += 2) {
			nibbles[0] = (byte)instring.charAt(i+0);
			nibbles[1] = (byte)instring.charAt(i+1);
			if (notHex(nibbles[0])) errors = 1;
			if (notHex(nibbles[1])) errors = 1;
			outarray[outoffset] = (byte)((hex2bin(nibbles[0]) << 4) | hex2bin(nibbles[1]));
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

