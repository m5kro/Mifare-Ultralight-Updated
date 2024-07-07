package com.samsung.sprc.fileselector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.tech.NfcA;
import android.net.Uri;

public class FileSelectorActivity extends Activity {
	private EditTextCursorWatcher mTextBuffer;
	private TextView mTextPage, mTextTotal;
	private Button mReadButton, mWriteButton, mLoadButton, mSaveButton, mLinkButton;
	private FileInputStream ifs;
	private FileOutputStream ofs;
	private File file;
	
	final String[] mFileFilter = { ".txt", "*.*" }; // file types
	
	private NfcAdapter mNfcAdapter;
	private IntentFilter tech;
	private IntentFilter[] intentFiltersArray;
	private PendingIntent pendingIntent;
	private Intent intent;
	private AlertDialog alertDialog, infoDialog, promptDialog;
	
	private static final int ACTION_NONE  = 0;
	private static final int ACTION_READ  = 1;
	private static final int ACTION_WRITE = 2;
	private int scanAction;
	
	// list of NFC technologies detected:
	private final String[][] techListsArray = new String[][] {
		new String[] {
			//MifareUltralight.class.getName(),
			NfcA.class.getName()
		}
	};
	
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		mTextBuffer  = (EditTextCursorWatcher) findViewById(R.id.text_buffer);
		mTextPage    = (TextView) findViewById(R.id.text_page);
		mTextTotal   = (TextView) findViewById(R.id.text_total);
		mReadButton  = (Button) findViewById(R.id.button_read);
		mWriteButton = (Button) findViewById(R.id.button_write);
		mLoadButton  = (Button) findViewById(R.id.button_load);
		mSaveButton  = (Button) findViewById(R.id.button_save);
		mLinkButton  = (Button) findViewById(R.id.button_link);
		
		mReadButton.setOnClickListener(mReadTagListener);
		mWriteButton.setOnClickListener(mWriteTagListener);
		
		mLoadButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				new FileSelector(FileSelectorActivity.this, FileOperation.LOAD, mLoadFileListener, mFileFilter).show();
			}
		});
		
		mSaveButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				new FileSelector(FileSelectorActivity.this, FileOperation.SAVE, mSaveFileListener, mFileFilter).show();
			}
		});
		
		mLinkButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(final View v) {
				Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://emutag.com"));
				startActivity(browserIntent);
			}
		});
		
		mTextBuffer.setOnSelectionChangedListener(new EditTextCursorWatcher.OnSelChangedListener() {
			@Override
			public void onSelChanged(int selStart, int selEnd) {
				String pageText = "Page ";
				int pageDec = mTextBuffer.getLayout().getLineForOffset(mTextBuffer.getSelectionStart());
				if (pageDec < 100) pageText = pageText + " ";
				if (pageDec < 10)  pageText = pageText + " ";
				byte[] pageHex = { (byte)pageDec };
				pageText = pageText + pageDec + " [0x" + ByteArrayToHexString(pageHex) + "] /";
				mTextPage.setText(pageText);
			}
		});
		
		mTextBuffer.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) { };
			public void beforeTextChanged(CharSequence s, int start, int count, int after) { };
			
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				String totalText = " Total ";
				//int totalDec = mTextBuffer.getLineCount();
				String[] lineCount = mTextBuffer.getText().toString().split(System.getProperty("line.separator"));
				int totalDec = lineCount.length;
				if (totalDec < 100) totalText = totalText + " ";
				if (totalDec < 10)  totalText = totalText + " ";
				byte[] totalHex = { (byte)totalDec };
				totalText = totalText + totalDec + " [0x" + ByteArrayToHexString(totalHex) + "]";
				mTextTotal.setText(totalText);
			}
		});
		
		/*
		mTextBuffer.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				mTextStatus.setText("Page " + mTextBuffer.getLayout().getLineForOffset(mTextBuffer.getSelectionStart()));
				return false;
			}
		});
		
		mTextBuffer.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent event) {
				mTextStatus.setText("Page " + mTextBuffer.getLayout().getLineForOffset(mTextBuffer.getSelectionStart()));
				return false;
			}
		});
		*/
		
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (mNfcAdapter == null) {
			// Stop here, we definitely need NFC
			Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		
		if (!mNfcAdapter.isEnabled()) {
			Toast.makeText(this, "Please activate NFC and press Back to return to the application!", Toast.LENGTH_LONG).show();
	        startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
		}
		
		tech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
		intentFiltersArray = new IntentFilter[] {tech};
		intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		//FLAG_ACTIVITY_REORDER_TO_FRONT FLAG_RECEIVER_REPLACE_PENDING
		pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
		
		scanAction = ACTION_NONE;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		// creating pending intent:
		//PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		// creating intent receiver for NFC events:
		//IntentFilter filter = new IntentFilter();
		//IntentFilter tech = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
		//IntentFilter[] intentFiltersArray = new IntentFilter[] {tech};
		//filter.addAction(NfcAdapter.ACTION_TECH_DISCOVERED);
		// enabling foreground dispatch for getting intent from NFC event:
		//NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		
		mNfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, this.techListsArray);
	}
	
	@Override
	protected void onPause() {
		// disabling foreground dispatch:
		//NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		mNfcAdapter.disableForegroundDispatch(this);
		super.onPause();
	}
	
	@Override
	protected void onNewIntent(Intent intent) {
		if (intent.getAction().equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
			if ((alertDialog != null) && alertDialog.isShowing()) alertDialog.dismiss();
			
			String mTextBufferText = mTextBuffer.getText().toString();
			
			NfcThread nfcThread = new NfcThread(intent, scanAction, mTextBufferText, mTextBufferHandler, mToastShortHandler, mToastLongHandler, mShowInfoDialogHandler);
			nfcThread.start();
			
			scanAction = ACTION_NONE;
		}
	}
	
	@SuppressLint("HandlerLeak")
	private Handler mTextBufferHandler = new Handler() {
		public void handleMessage(Message msg) {
			String text = (String)msg.obj;
			mTextBuffer.setText(text);
		}
	};
	
	@SuppressLint("HandlerLeak")
	private Handler mToastShortHandler = new Handler() {
		public void handleMessage(Message msg) {
			String text = (String)msg.obj;
			Toast.makeText(FileSelectorActivity.this, text, Toast.LENGTH_SHORT).show();
		}
	};
	
	@SuppressLint("HandlerLeak")
	private Handler mToastLongHandler = new Handler() {
		public void handleMessage(Message msg) {
			String text = (String)msg.obj;
			Toast.makeText(FileSelectorActivity.this, text, Toast.LENGTH_LONG).show();
		}
	};
	
	@SuppressLint("HandlerLeak")
	private Handler mShowInfoDialogHandler = new Handler() {
		public void handleMessage(Message msg) {
			String text = (String)msg.obj;
			infoDialog = showInfoDialog(text);
			infoDialog.show();
		}
	};
	
	Button.OnClickListener mReadTagListener = new Button.OnClickListener() {
		public void onClick(View arg0) {
			scanAction = ACTION_READ;
			alertDialog = showAlertDialog("Waiting for MIFARE Ultralight® - compatible tag to READ...");
			alertDialog.show();
			//alertDialog.setCancelable(false);
		}
	};
	
	Button.OnClickListener mWriteTagListener = new Button.OnClickListener() {
		public void onClick(View arg0) {
			scanAction = ACTION_WRITE;
			alertDialog = showAlertDialog("Waiting for MIFARE Ultralight® - compatible tag to WRITE...");
			alertDialog.show();
			//alertDialog.setCancelable(false);
		}
	};
	
	OnHandleFileListener mLoadFileListener = new OnHandleFileListener() {
		@Override
		public void handleFile(final String filePath) {
			file = new File(filePath);
			try {
				ifs = new FileInputStream(file);
				byte[] fileBuffer = new byte[(int) file.length()];
				ifs.read(fileBuffer);
				String fileString = new String(fileBuffer);
				mTextBuffer.setText(fileString);
				ifs.close();
				Toast.makeText(FileSelectorActivity.this, "Load: " + filePath, Toast.LENGTH_SHORT).show();
			}
			catch (Exception e) {
				Toast.makeText(FileSelectorActivity.this, "Error loading: " + filePath, Toast.LENGTH_SHORT).show();
			}
		}
	};
	
	OnHandleFileListener mSaveFileListener = new OnHandleFileListener() {
		@Override
		public void handleFile(final String filePath) {
			file = new File(filePath);
			
			if (file.exists()) {
				promptDialog = showPromptDialog("File Exists", "Overwrite \"" + filePath + "\" ?", "Overwrite", filePath);
				promptDialog.show();
			}
			else {
				writeFile(filePath);
			}
		}
	};
	
	private void writeFile(String filePath) {
		file = new File(filePath);
		
		try {
			ofs = new FileOutputStream(file);
			ofs.write(mTextBuffer.getText().toString().getBytes());
			ofs.close();
			Toast.makeText(FileSelectorActivity.this, "Save: " + filePath, Toast.LENGTH_SHORT).show();
		}
		catch (Exception e) {
			Toast.makeText(FileSelectorActivity.this, "Error saving: " + filePath, Toast.LENGTH_SHORT).show();
		}
	}
	
	private AlertDialog showAlertDialog(String message) {
		DialogInterface.OnClickListener dialogInterfaceListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				alertDialog.cancel();
				scanAction = ACTION_NONE;
			}
		};
		
		alertDialog = new AlertDialog.Builder(this)
			.setTitle("Scan MIFARE® Tag")
			.setIcon(R.drawable.ic_launcher)
			.setMessage(message)
			.setNegativeButton("Cancel", dialogInterfaceListener)
			.create();
		
		return alertDialog;
	}
	
	private AlertDialog showInfoDialog(String message) {
		DialogInterface.OnClickListener dialogInterfaceListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				infoDialog.cancel();
			}
		};
		
		infoDialog = new AlertDialog.Builder(this)
			.setTitle("Information")
			.setIcon(R.drawable.ic_launcher)
			.setMessage(message)
			.setPositiveButton("OK", dialogInterfaceListener)
			.create();
		
		return infoDialog;
	}
	
	private AlertDialog showPromptDialog(String title, String message, String confirmText, final String filePath) {
		DialogInterface.OnClickListener dialogInterfacePosListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				promptDialog.cancel();
				writeFile(filePath);
			}
		};
		
		DialogInterface.OnClickListener dialogInterfaceNegListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				promptDialog.cancel();
			}
		};
		
		promptDialog = new AlertDialog.Builder(this)
			.setTitle(title)
			.setIcon(R.drawable.ic_launcher)
			.setMessage(message)
			.setPositiveButton(confirmText, dialogInterfacePosListener)
			.setNegativeButton("Cancel", dialogInterfaceNegListener)
			.create();
		
		return promptDialog;
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
}

