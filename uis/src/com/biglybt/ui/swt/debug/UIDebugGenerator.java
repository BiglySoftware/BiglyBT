/*
 * Created on May 28, 2006 4:31:42 PM
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.biglybt.ui.swt.debug;

import java.io.*;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.biglybt.ui.swt.mainwindow.Colors;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.logging.impl.FileLogging;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPiece;
import com.biglybt.core.util.*;
import com.biglybt.platform.PlatformManagerFactory;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.shell.ShellFactory;
import com.biglybt.ui.swt.shells.CoreWaiterSWT;
import com.biglybt.ui.swt.shells.CoreWaiterSWT.TriggerInThread;
import com.biglybt.ui.swt.skin.SWTSkinObject;
import com.biglybt.util.DataSourceUtils;
import com.biglybt.ui.swt.shells.MessageBoxShell;

import com.biglybt.core.*;
import com.biglybt.ui.UserPrompterResultListener;

/**
 * @author TuxPaper
 * @created May 28, 2006
 *
 */
public class UIDebugGenerator
{
	public static class GeneratedResults
	{
		public File file;

		public String message;

		public String email;
	}

	public static void 
	generate(
		final String sourceRef, String additionalText) 
	{
		final GeneratedResults gr = generate(null,
				new DebugPrompterListener() {
					@Override
					public boolean promptUser(GeneratedResults gr) {
						UIDebugGenerator.promptUser(false, gr);
						if (gr.message == null) {
							return false;
						}
						return true;
					}
				});
		if (gr != null) {
			MessageBoxShell mb = new MessageBoxShell(SWT.OK | SWT.CANCEL
					| SWT.ICON_INFORMATION | SWT.APPLICATION_MODAL,
					"UIDebugGenerator.complete", new String[] {
						gr.file.toString()
					});
			mb.open(new UserPrompterResultListener() {
				@Override
				public void prompterClosed(int result) {
					if (result == SWT.OK) {
						try {
							PlatformManagerFactory.getPlatformManager().showFile(
									gr.file.getAbsolutePath());
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			});
		}
	}

	public interface DebugPrompterListener {
		public boolean promptUser(GeneratedResults gr);
	}

	public static java.util.List<Image>
	getShellImages()
	{
		java.util.List<Image> result = new ArrayList<>();
				
		Display display = Display.getCurrent();
		if (display == null) {
			return null;
		}

		Shell activeShell = display.getActiveShell();
		if (activeShell != null) {
			activeShell.setCursor(display.getSystemCursor(SWT.CURSOR_WAIT));
		}

		Shell[] shells = display.getShells();
		if (shells == null || shells.length == 0) {
			return null;
		}

		for (int i = 0; i < shells.length; i++) {
			try {
				Shell shell = shells[i];
				Image image = null;

				if (shell.isDisposed() || !shell.isVisible()) {
					continue;
				}

				shell.moveAbove( null );
				
				Utils.ensureDisplayUpdated();

				if (shell.getData("class") instanceof ObfuscateShell) {
					ObfuscateShell shellClass = (ObfuscateShell) shell.getData("class");

					try {
						image = shellClass.generateObfuscatedImage();
					} catch (Exception e) {
						Debug.out("Obfuscating shell " + shell, e);
					}
				} else {

					Rectangle clientArea = shell.getClientArea();
					image = new Image(display, clientArea.width, clientArea.height);

					GC gc = new GC(shell);
					try {
						gc.copyArea(image, clientArea.x, clientArea.y);
					} finally {
						gc.dispose();
					}
				}

				if (image != null) {
					result.add( image );
				}

			} catch (Throwable  e) {
				Logger.log(new LogEvent(LogIDs.GUI, "Creating Obfuscated Image", e));
			}
		}

		if (activeShell != null) {
			activeShell.setCursor(null);
		}
		
		return( result );
	}
	
	public static Image generateObfuscatedImage( Shell shell ) {
		// 3.2 TODO: Obfuscate! (esp advanced view)

		Rectangle shellBounds = shell.getBounds();
		Rectangle shellClientArea = shell.getClientArea();

		Display display = shell.getDisplay();
		if (display.isDisposed()) {
			return null;
		}
		Image fullImage = new Image(display, shellBounds.width, shellBounds.height);
		Image subImage = new Image(display, shellClientArea.width, shellClientArea.height);

		GC gc = new GC(display);
		try {
			gc.copyArea(fullImage, shellBounds.x, shellBounds.y);
		} finally {
			gc.dispose();
		}
		GC gcShell = new GC(shell);
		try {
			gcShell.copyArea(subImage, 0, 0);
		} finally {
			gcShell.dispose();
		}
		GC gcFullImage = new GC(fullImage);
		try {
			Point location = shell.toDisplay(0, 0);
			gcFullImage.drawImage(subImage, location.x - shellBounds.x, location.y
					- shellBounds.y);
		} finally {
			gcFullImage.dispose();
		}
		subImage.dispose();

		Control[] children = shell.getChildren();
		for (Control control : children) {
			SWTSkinObject so = (SWTSkinObject) control.getData("SkinObject");
			if (so instanceof ObfuscateImage) {
				ObfuscateImage oi = (ObfuscateImage) so;
				oi.obfuscatedImage(fullImage);
			}
		}

		Rectangle monitorClientArea = shell.getMonitor().getClientArea();
		Rectangle trimmedShellBounds = shellBounds.intersection(monitorClientArea);

		if (!trimmedShellBounds.equals(shellBounds)) {
			subImage = new Image(display, trimmedShellBounds.width,
					trimmedShellBounds.height);
			GC gcCrop = new GC(subImage);
			try {
				gcCrop.drawImage(fullImage, shellBounds.x - trimmedShellBounds.x,
						shellBounds.y - trimmedShellBounds.y);
			} finally {
				gcCrop.dispose();
				fullImage.dispose();
				fullImage = subImage;
			}
		}

		return fullImage;
	}
	
	public static GeneratedResults 
	generate(
		File[] extraLogDirs,
		DebugPrompterListener debugPrompterListener) 
	{
		final File path = new File(SystemProperties.getUserPath(), "debug");
		if (!path.isDirectory()) {
			path.mkdir();
		} else {
			try {
				File[] files = path.listFiles();
				for (int i = 0; i < files.length; i++) {
					files[i].delete();
				}
			} catch (Exception e) {
			}
		}

		java.util.List<Image> shell_images = getShellImages();
		
		for ( int i=0;i<shell_images.size();i++){
			
			Image image = shell_images.get( i );
			
			File file = new File(path, "image-" + i + ".vpg");
			String sFileName = file.getAbsolutePath();
	
			ImageLoader imageLoader = new ImageLoader();
			imageLoader.data = new ImageData[] {
				image.getImageData()
			};
			imageLoader.save(sFileName, SWT.IMAGE_JPEG);
		}
		
		GeneratedResults gr = new GeneratedResults();



		if (debugPrompterListener != null) {
			if (!debugPrompterListener.promptUser(gr)) {
				return null;
			}
		}

		FileWriter fw = null;
		try {
			File fUserMessage = new File(path, "usermessage.txt");

			fw = new FileWriter(fUserMessage);

			fw.write(gr.message  + "\n" + gr.email);

			fw.close();

			fw = null;

		} catch (Throwable e) {

			if ( fw != null ){
				try{
					fw.close();
				}catch( Throwable f ){
				}
			}
			e.printStackTrace();
		}

		CoreWaiterSWT.waitForCore(TriggerInThread.ANY_THREAD,
			new CoreRunningListener() {

				@Override
				public void coreRunning(Core core) {
					core.executeOperation(
						CoreOperation.OP_PROGRESS,
						new CoreOperationTask() {
							
							@Override
							public String getName(){
								return null;
							}
							
							@Override
							public DownloadManager 
							getDownload()
							{
								return null;
							}
							
							@Override
							public void run(CoreOperation operation) {
								try {

									File fEvidence = new File(path, "evidence.log");
									PrintWriter pw = new PrintWriter(fEvidence, "UTF-8");

									AEDiagnostics.generateEvidence(pw);

									pw.close();

								} catch (IOException e) {

									Debug.printStackTrace(e);
								}
							}

							@Override
							public ProgressCallback getProgressCallback() {
								return null;
							}
						});
				}
			});

		try {
			final File outFile = new File(SystemProperties.getUserPath(), "debug.zip");
			if (outFile.exists()) {
				outFile.delete();
			}

			AEDiagnostics.flushPendingLogs();

			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFile));

			// %USERDIR%\logs
			File logPath = new File(SystemProperties.getUserPath(), "logs");
			File[] files = logPath.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.getName().endsWith(".log");
				}
			});
			addFilesToZip(out, files);

			// %USERDIR%
			File userPath = new File(SystemProperties.getUserPath());
			files = userPath.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return pathname.getName().endsWith(".log");
				}
			});
			addFilesToZip(out, files);

			// %USERDIR%\debug
			files = path.listFiles();
			addFilesToZip(out, files);

			// recent errors from exe dir
			final long ago = SystemTime.getCurrentTime() - 1000L * 60 * 60 * 24 * 90;
			File appPath = new File(SystemProperties.getApplicationPath());
			files = appPath.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					return (pathname.getName().startsWith("hs_err") && pathname.lastModified() > ago);
				}
			});
			addFilesToZip(out, files);

			// recent crashes from temp dir

			try{
				File temp_file = File.createTempFile( "AZU", "tmp" );

				files = temp_file.getParentFile().listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return (pathname.getName().startsWith("hs_err") && pathname.lastModified() > ago);
					}
				});
				addFilesToZip(out, files);
				temp_file.delete();
			}catch( Throwable e ){
			}

			// recent errors from OSX java log dir
			File javaLogPath = new File(System.getProperty("user.home"), "Library"
					+ File.separator + "Logs" + File.separator + "Java");
			if (javaLogPath.isDirectory()) {
				files = javaLogPath.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return (pathname.getName().endsWith("log") && pathname.lastModified() > ago);
					}
				});
				addFilesToZip(out, files);
			}

			// recent OSX crashes
			File diagReportspath = new File(System.getProperty("user.home"), "Library"
					+ File.separator + "Logs" + File.separator + "DiagnosticReports");
			if (diagReportspath.isDirectory()) {
				files = diagReportspath.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return (pathname.getName().endsWith("crash") && pathname.lastModified() > ago);
					}
				});
				addFilesToZip(out, files);
			}

			boolean bLogToFile = COConfigurationManager.getBooleanParameter("Logging Enable");
			String sLogDir = COConfigurationManager.getStringParameter("Logging Dir",
					"");
			if (bLogToFile && sLogDir != null) {
				File loggingFile = new File(sLogDir, FileLogging.LOG_FILE_NAME);
				if (loggingFile.isFile()) {
					addFilesToZip(out, new File[] {
						loggingFile
					});
				}
			}

			if (extraLogDirs != null) {
				for (File file : extraLogDirs) {
					if (!file.isDirectory()) {
						continue;
					}
					files = file.listFiles(new FileFilter() {
						@Override
						public boolean accept(File pathname) {
							return pathname.getName().endsWith("stackdump")
									|| pathname.getName().endsWith("log");
						}
					});
					addFilesToZip(out, files);
				}
			}

			out.close();

			if (outFile.exists()) {
				gr.file = outFile;
				return gr;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private static void promptUser(final boolean allowEmpty, GeneratedResults gr) {
		final Shell shell = ShellFactory.createShell(Utils.findAnyShell(), SWT.SHELL_TRIM);

		final String[] text = { null, null };
		final int[] sendMode = {-1};

		Utils.setShellIcon(shell);

		Messages.setLanguageText(shell, "UIDebugGenerator.messageask.title");

		shell.setLayout(new FormLayout());

		Label lblText = new Label(shell, SWT.NONE);
		Messages.setLanguageText(lblText, "UIDebugGenerator.messageask.text");

		final Text textMessage = new Text(shell, SWT.MULTI | SWT.BORDER | SWT.WRAP);
		final Text textEmail = new Text(shell, SWT.BORDER);

		textEmail.setMessage("optional@email.here");

		Composite cButtonsSuper = new Composite(shell, SWT.NONE);
		GridLayout gl = new GridLayout();
		cButtonsSuper.setLayout(gl);

		Composite cButtons = new Composite(cButtonsSuper, SWT.NONE);
		cButtons.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
		cButtons.setLayout(new RowLayout());

		Button btnSendLater = new Button(cButtons, SWT.PUSH);
		btnSendLater.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (emptyCheck(textMessage, allowEmpty)) {
  				text[0] = textMessage.getText();
  				text[1] = textEmail.getText();
  				sendMode[0] = 1;
				}
				shell.dispose();
			}
		});
		Button btnCancel = new Button(cButtons, SWT.PUSH);
		btnCancel.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				shell.dispose();
			}
		});

		if (Constants.isOSX) {
			btnCancel.moveAbove(null);
		}
		Messages.setLanguageText(btnCancel, "Button.cancel");
		Messages.setLanguageText(btnSendLater, "Button.sendManual");

		FormData fd;

		fd = new FormData();
		fd.top = new FormAttachment(0, 5);
		fd.left = new FormAttachment(0, 5);
		fd.right = new FormAttachment(100, -5);
		lblText.setLayoutData(fd);

		fd = new FormData();
		fd.top = new FormAttachment(lblText, 10);
		fd.left = new FormAttachment(0, 5);
		fd.right = new FormAttachment(100, -5);
		fd.bottom = new FormAttachment(textEmail, -10);
		textMessage.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(0, 5);
		fd.right = new FormAttachment(100, -5);
		fd.bottom = new FormAttachment(cButtonsSuper, -2);
		textEmail.setLayoutData(fd);

		fd = new FormData();
		fd.left = new FormAttachment(0, 5);
		fd.right = new FormAttachment(100, -5);
		fd.bottom = new FormAttachment(100, -1);
		cButtonsSuper.setLayoutData(fd);

		textMessage.setFocus();

		shell.setSize(500, 300);
		shell.layout();
		Utils.centreWindow(shell);
		shell.open();

		Utils.readAndDispatchLoop( shell );

		if (sendMode[0] != -1) {
			gr.message = text[0];
			gr.email = text[1];
		}
	}

	/**
	 * @param textMessage
	 * @param allowEmpty
	 * @return
	 *
	 * @since 4.5.0.3
	 */
	protected static boolean emptyCheck(Text textMessage, boolean allowEmpty) {
		if (allowEmpty) {
			return true;
		}
		if (textMessage.getText().length() > 0) {
			return true;
		}

		new MessageBoxShell(SWT.OK, "UIDebugGenerator.message.cancel",
				(String[]) null).open(null);

		return false;
	}

	private static void addFilesToZip(ZipOutputStream out, File[] files) {
		byte[] buf = new byte[1024];
		if (files == null) {
			return;
		}

		for (int j = 0; j < files.length; j++) {
			File file = files[j];

			FileInputStream in;
			try {
				in = new FileInputStream(file);
			} catch (FileNotFoundException e) {
				continue;
			}

			try {
				ZipEntry entry = new ZipEntry(file.getName());
				entry.setTime(file.lastModified());
				out.putNextEntry(entry);
				//	Transfer bytes from the file to the ZIP file
				int len;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}

				// Complete the entry
				out.closeEntry();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			try {
				in.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * @param image
	 * @param bounds
	 */
	public static void obfuscateArea(Image image, Rectangle bounds) {
		GC gc = new GC(image);
		try {
			gc.setBackground(image.getDevice().getSystemColor(SWT.COLOR_WHITE));
			gc.setForeground(image.getDevice().getSystemColor(SWT.COLOR_RED));
			gc.fillRectangle(bounds);
			gc.drawRectangle(bounds);
			int x2 = bounds.x + bounds.width;
			int y2 = bounds.y + bounds.height;
			gc.drawLine(bounds.x, bounds.y, x2, y2);
			gc.drawLine(x2, bounds.y, bounds.x, y2);
		} finally {
			gc.dispose();
		}
	}

	/**
	 * @param image
	 * @param bounds
	 * @param text
	 */
	public static void obfuscateArea(Image image, Rectangle bounds, String text) {

		if (bounds.isEmpty())
			return;

		if (text == null || text.length() == 0) {
			obfuscateArea(image, bounds);
			return;
		}

		GC gc = new GC(image);
		try {
			Device device = image.getDevice();
			gc.setBackground(Colors.getSystemColor(device, SWT.COLOR_WHITE));
			gc.setForeground(Colors.getSystemColor(device, SWT.COLOR_RED));
			gc.fillRectangle(bounds);
			gc.drawRectangle(bounds);
			Utils.setClipping(gc, bounds);
			gc.drawText(text, bounds.x + 2, bounds.y + 1);
		} finally {
			gc.dispose();
		}
	}

	/**
	 * @param image
	 * @param control
	 * @param shellOffset
	 * @param text
	 */
	public static void obfuscateArea(Image image, Control control, String text) {
		if ( control.isDisposed()){
			return;
		}
		Rectangle bounds = control.getBounds();
		Point location = Utils.getLocationRelativeToShell(control);
		bounds.x = location.x;
		bounds.y = location.y;

		obfuscateArea(image, bounds, text);
	}
	
	public static String
	obfuscateDownloadName(
		Object		ds )
	{
		return( obfuscateDownloadName( DataSourceUtils.getDM( ds )));
	}
	
	public static String
	obfuscateDownloadName(
		PEPeer	peer )
	{
		if (peer == null) return( "" );
		PEPeerManager manager = peer.getManager();
		if (manager == null) return( "" );
		String name = manager.getDisplayName();
		if ( name.length() > 3 ){
			return( name.substring( 0,  3));
		}else{
			return( name );
		}
	}
	
	public static String
	obfuscateDownloadName(
		PEPiece	piece )
	{
		if (piece == null) return( "" );
		PEPeerManager manager = piece.getManager();
		if (manager == null) return( "" );
		String name = manager.getDisplayName();
		if ( name.length() > 3 ){
			return( name.substring( 0,  3));
		}else{
			return( name );
		}
	}
	
	public static String
	obfuscateDownloadName(
		DownloadManager	dm )
	{
		String name = null;
		if (dm != null) {
			name = dm.toString();
			int i = name.indexOf('#');
			if (i > 0) {
				name = name.substring(i + 1);
			}
		}

		if (name == null)
			name = "";
		return name;
	}
	
	public static String
	obfuscateFileName(
		DiskManagerFileInfo	fileInfo )
	{
		return fileInfo.getIndex() + ": " + Debug.secretFileName(fileInfo.getFile(true).getName());
	}
}
