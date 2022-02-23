/*
 * Created on 29 juin 2003
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
 *
 */
package com.biglybt.ui.swt;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.util.AENetworkClassifier;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.core.util.SystemTime;
import com.biglybt.pif.peers.Peer;
import com.biglybt.pif.utils.LocationProvider;
import com.biglybt.pifimpl.local.PluginCoreUtils;

import com.biglybt.core.CoreFactory;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.ui.skin.SkinProperties;
import com.biglybt.ui.swt.imageloader.ImageLoader;

/**
 * @author Olivier
 *
 */
public class ImageRepository
{
	private static final String[] noCacheExtList = new String[] {
		".exe"
	};

	private static final boolean forceNoAWT = Constants.isOSX || Constants.isWindows;

	/**public*/
	static void addPath(String path, String id) {
		SkinProperties[] skinProperties = ImageLoader.getInstance().getSkinProperties();
		if (skinProperties != null && skinProperties.length > 0) {
			skinProperties[0].addProperty(id, path);
		}
	}

	/**
	   * Gets an image for a file associated with a given program
	   *
	   */
	
	private static volatile Set<String>	ignore_icon_exts;
	
	static{
		
		COConfigurationManager.addWeakParameterListener(
				(n)->{
					String val = COConfigurationManager.getStringParameter( n );
					
					val = val.replace( ';', ' ' );
					val = val.replace( ',', ' ' );
					
					val = val.toLowerCase( Locale.US );
					
					String[] bits = val.split( " " );
					
					Set<String> exts = new HashSet<>();
					
					for ( String b: bits ){
						
						b = b.trim();
						
						if ( !b.isEmpty()){
														
							if ( !b.startsWith( "." )){
								
								b = "." + b;
							}
							
							exts.add( b );
						}
					}
					
					ignore_icon_exts = exts;
				},
				true,
				"Ignore Icon Exts" );		
	}
	
	public static Image getIconFromExtension(File file, String ext, boolean bBig,
			boolean minifolder) {
		Image image = null;

		try {
			String key = "osicon" + ext;

			if (bBig)
				key += "-big";
			if (minifolder)
				key += "-fold";

			image = ImageLoader.getInstance().getImage(key);
			if (ImageLoader.isRealImage(image)) {
				return image;
			}

			ImageLoader.getInstance().releaseImage(key);
			image = null;

			ImageData imageData = null;

			if (Constants.isWindows) {
					
					// Alcohol causing crashes on various file types. Really can't be bothered
				
				if ( ignore_icon_exts.contains( ext.toLowerCase( Locale.US  ))){
					
					return ImageLoader.getInstance().getImage(minifolder ? "folder" : "transparent");
				}
				
				try {
					//Image icon = Win32UIEnhancer.getFileIcon(new File(path), big);

					Class<?> enhancerClass = Class.forName("com.biglybt.ui.swt.win32.Win32UIEnhancer");
					Method method = enhancerClass.getMethod("getFileIcon",
							new Class[] {
								File.class,
								boolean.class
							});
					image = (Image) method.invoke(null, new Object[] {
						file,
						bBig
					});
					if (image != null) {
						if (!bBig)
							image = force16height(image);
						if (minifolder)
							image = minifolderize(file.getParent(), image, bBig);
						ImageLoader.getInstance().addImageNoDipose(key, image);
						return image;
					}
				} catch (Exception e) {
					Debug.printStackTrace(e);
				}
			} else if (Constants.isOSX) {
				try {
					Class<?> enhancerClass = Class.forName("com.biglybt.ui.swt.osx.CocoaUIEnhancer");
					Method method = enhancerClass.getMethod("getFileIcon",
							new Class[] {
								String.class,
								int.class
							});
					image = (Image) method.invoke(null, new Object[] {
						file.getAbsolutePath(),
						(int) (bBig ? 128 : 16)
					});
					if (image != null) {
						if (!bBig)
							image = force16height(image);
						if (minifolder)
							image = minifolderize(file.getParent(), image, bBig);
						ImageLoader.getInstance().addImageNoDipose(key, image);
						return image;
					}
				} catch (Throwable t) {
					Debug.printStackTrace(t);
				}
			}

			if (imageData == null) {
				Program program = Program.findProgram(ext);
				if (program != null) {
					imageData = program.getImageData();
				}
			}

			if (imageData != null) {
				image = new Image(Display.getDefault(), imageData);
				if (!bBig)
					image = force16height(image);
				if (minifolder)
					image = minifolderize(file.getParent(), image, bBig);

				ImageLoader.getInstance().addImageNoDipose(key, image);
			}
		} catch (Throwable e) {
			// seen exceptions thrown here, due to images.get failing in Program.hashCode
			// ignore and use default icon
		}

		if (image == null) {
			return ImageLoader.getInstance().getImage(minifolder ? "folder" : "transparent");
		}
		return image;
	}

	private static Image minifolderize(String path, Image img, boolean big) {
		Image imgFolder =  ImageLoader.getInstance().getImage(big ? "folder" : "foldersmall");
		Rectangle folderBounds = imgFolder.getBounds();
		Rectangle dstBounds = img.getBounds();
		Image tempImg = Utils.renderTransparency(Display.getCurrent(), img,
				imgFolder, new Point(dstBounds.width - folderBounds.width,
						dstBounds.height - folderBounds.height), 204);
		if (tempImg != null) {
			img.dispose();
			img = tempImg;
		}
		return img;
	}

	private static Image force16height(Image image) {
		if (image == null) {
			return image;
		}

		Rectangle bounds = image.getBounds();
		if (bounds.height != 16) {
			Image newImage = new Image(image.getDevice(), 16, 16);
			GC gc = new GC(newImage);
			try {
				if (!Constants.isUnix) {
					// drawImage doesn't work on GTK when advanced is on
					gc.setAdvanced(true);
				}

				gc.drawImage(image, 0, 0, bounds.width, bounds.height, 0, 0, 16, 16);
			} finally {
				gc.dispose();
			}

			image.dispose();
			image = newImage;
		}

		return image;
	}

	/**
	* <p>Gets an iconic representation of the file or directory at the path</p>
	* <p>For most platforms, the icon is a 16x16 image; weak-referencing caching is used to avoid abundant reallocation.</p>
	* @param path Absolute path to the file or directory
	* @return The image
	*/
	public static Image getPathIcon(final String path, boolean bBig,
			boolean minifolder) {
		if (path == null)
			return null;

		File file = null;
		boolean bDeleteFile = false;

		boolean noAWT = forceNoAWT || !bBig;

		try {
			file = new File(path);

			// workaround for unsupported platforms
			// notes:
			// Mac OS X - Do not mix AWT with SWT (possible workaround: use IPC/Cocoa)

			String key;
			if (FileUtil.isDirectoryWithTimeout( file )) {
				if (noAWT) {
					if (Constants.isWindows || Constants.isOSX) {
						return getIconFromExtension(file, "-folder", bBig, false);
					}
					return ImageLoader.getInstance().getImage("folder");
				}

				key = file.getPath();
			} else {
				final int idxDot = file.getName().lastIndexOf(".");

				if (idxDot == -1) {
					if (noAWT) {
						return getIconFromExtension(file, "", bBig, false);
					}

					key = "?!blank";
				} else {
					final String ext = file.getName().substring(idxDot);
					key = ext;

					if (noAWT)
						return getIconFromExtension(file, ext, bBig, minifolder);

					// case-insensitive file systems
					for (int i = 0; i < noCacheExtList.length; i++) {
						if (noCacheExtList[i].equalsIgnoreCase(ext)) {
							key = file.getPath();
							break;
						}
					}
				}
			}

			if (bBig)
				key += "-big";
			if (minifolder)
				key += "-fold";

			key = "osicon" + key;

			// this method mostly deals with incoming torrent files, so there's less concern for
			// custom icons (unless user sets a custom icon in a later session)

			// other platforms - try sun.awt
			Image image = ImageLoader.getInstance().getImage(key);
			if (ImageLoader.isRealImage(image)) {
				return image;
			}
			ImageLoader.getInstance().releaseImage(key);
			image = null;

			bDeleteFile = !file.exists();
			if (bDeleteFile) {
				file = File.createTempFile("AZ_", FileUtil.getExtension(path));
			}

			java.awt.Image awtImage = null;

			try {
  			final Class sfClass = Class.forName("sun.awt.shell.ShellFolder");
  			if (sfClass != null && file != null) {
  				Method method = sfClass.getMethod("getShellFolder", new Class[] {
  					File.class
  				});
  				if (method != null) {
  					Object sfInstance = method.invoke(null, new Object[] {
  						file
  					});

  					if (sfInstance != null) {
  						method = sfClass.getMethod("getIcon", new Class[] {
  							Boolean.TYPE
  						});
  						if (method != null) {
  							awtImage = (java.awt.Image) method.invoke(sfInstance,
  									new Object[] {
										  Boolean.valueOf(bBig)
  									});
  						}
  					}
  				}
  			}
			} catch (Throwable e) {
			}

			if (awtImage != null) {
				final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
				ImageIO.write((BufferedImage) awtImage, "png", outStream);
				final ByteArrayInputStream inStream = new ByteArrayInputStream(
						outStream.toByteArray());

				image = new Image(Display.getDefault(), inStream);
				if (!bBig) {
					image = force16height(image);
				}
				if (minifolder)
					image = minifolderize(file.getParent(), image, bBig);


				ImageLoader.getInstance().addImageNoDipose(key, image);

				if (bDeleteFile && file != null && file.exists()) {
					file.delete();
				}
				return image;
			}
		} catch (Exception e) {
			//Debug.printStackTrace(e);
		}

		if (bDeleteFile && file != null && file.exists()) {
			file.delete();
		}

		// Possible scenario: Method call before file creation
		String ext = FileUtil.getExtension(path);
		if (ext.length() == 0) {
			return ImageLoader.getInstance().getImage("folder");
		}

		return getIconFromExtension(file, ext, bBig, minifolder);
	}

	private static LocationProvider	flag_provider;
	private static long				flag_provider_last_check;

	private static Image	flag_none		= ImageLoader.getNoImage();
	private static Object	flag_small_key 	= new Object();
	private static Object	flag_big_key 	= new Object();

	private static Map<String,Image>	flag_cache = new HashMap<>();

	private static LocationProvider
	getFlagProvider()
	{
		if ( flag_provider != null ){

			if ( flag_provider.isDestroyed()){

				flag_provider 				= null;
				flag_provider_last_check	= 0;
			}
		}

		if ( flag_provider == null ){

			long	now = SystemTime.getMonotonousTime();

			if ( flag_provider_last_check == 0 || now - flag_provider_last_check > 20*1000 ){

				flag_provider_last_check = now;

				java.util.List<LocationProvider> providers = CoreFactory.getSingleton().getPluginManager().getDefaultPluginInterface().getUtilities().getLocationProviders();

				for ( LocationProvider provider: providers ){

					if ( 	provider.hasCapabilities(
								LocationProvider.CAP_ISO3166_BY_IP |
								LocationProvider.CAP_FLAG_BY_IP )){

						flag_provider = provider;
					}
				}
			}
		}

		return( flag_provider );
	}

	public static boolean
	hasCountryFlags(
		boolean		small )
	{
		if ( !Utils.isSWTThread()){

			Debug.out( "Needs to be swt thread..." );

			return( false );
		}

		LocationProvider fp = getFlagProvider();

		if ( fp == null ){

			return( false );
		}

		return( true );
	}

	public static Image
	getCountryFlag(
		Peer		peer,
		boolean		small )
	{
		return( getCountryFlag( PluginCoreUtils.unwrap( peer ), small ));
	}

	private static Map<String,Image>	net_images = new HashMap<>();

	public static Image
	getCountryFlag(
		PEPeer		peer,
		boolean		small )
	{
		if ( peer == null ){

			return( null );
		}

		Object	peer_key = small?flag_small_key:flag_big_key;

		Image flag = (Image)peer.getUserData( peer_key );

		if ( flag == null ){

			LocationProvider fp = getFlagProvider();

			if ( fp != null ){

				try{
					String ip = peer.getIp();

					if ( HostNameToIPResolver.isDNSName( ip )){

						InetAddress peer_address = HostNameToIPResolver.syncResolve( ip );

						String cc_key = fp.getISO3166CodeForIP( peer_address ) + (small?".s":".l");

						flag = flag_cache.get( cc_key );

						if ( flag != null ){

							peer.setUserData( peer_key, flag );

						}else{

							InputStream is = fp.getCountryFlagForIP( peer_address, small?0:1 );

							if ( is != null ){

								try{
									Display display = Display.getDefault();

									flag = new Image( display, is);

									//System.out.println( "Created flag image for " + cc_key );

								}finally{

									is.close();
								}
							}else{

								flag = flag_none;
							}

							flag_cache.put( cc_key, flag );

							peer.setUserData( peer_key, flag );
						}
					}else{

						String cat =  AENetworkClassifier.categoriseAddress( ip );

						if ( cat != AENetworkClassifier.AT_PUBLIC ){

							final String key = "net_" + cat + (small?"_s":"_b" );

							Image i = net_images.get( key );

							if ( i == null ){

								Utils.execSWTThread(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											Image i = ImageLoader.getInstance().getImage( key );

											net_images.put( key, i );
										}
									},
									false );

								i = net_images.get( key );
							}

							if ( ImageLoader.isRealImage( i )){

								return( i );
							}
						}
					}

				}catch( Throwable e ){

				}
			}
		}

		if ( flag == flag_none ){

			return( null );
		}

		return( flag );
	}

	public static Image
	getCountryFlag(
		InetAddress		address,
		boolean			small )
	{
		if ( address == null ){

			return( null );
		}

		Image flag = null;

		LocationProvider fp = getFlagProvider();

		if ( fp != null ){

			try{
				String cc_key = fp.getISO3166CodeForIP( address ) + (small?".s":".l");

				flag = flag_cache.get( cc_key );

				if ( flag == null ){

					InputStream is = fp.getCountryFlagForIP( address, small?0:1 );

					if ( is != null ){

						try{
							Display display = Display.getDefault();

							flag = new Image( display, is);

							//System.out.println( "Created flag image for " + cc_key );

						}finally{

							is.close();
						}
					}else{

						flag = flag_none;
					}

					flag_cache.put( cc_key, flag );
				}

			}catch( Throwable e ){

			}
		}

		if ( flag == flag_none ){

			return( null );
		}

		return( flag );
	}

	public static Image
	getCountryFlag(
		String			cc,
		boolean			small )
	{
		if ( cc == null ){

			return( null );
		}

		if ( AENetworkClassifier.internalise( cc ) == cc ){
			
			final String key = "net_" + cc + (small?"_s":"_b" );

			Image i = net_images.get( key );

			if ( i == null ){

				Utils.execSWTThread(
					new Runnable()
					{
						@Override
						public void
						run()
						{
							Image i = ImageLoader.getInstance().getImage( key );

							net_images.put( key, i );
						}
					},
					false );

				i = net_images.get( key );
			}

			if ( ImageLoader.isRealImage( i )){

				return( i );
			}
		}
		
		Image flag = null;

		LocationProvider fp = getFlagProvider();

		if ( fp != null ){

			try{
				String cc_key = cc + (small?".s":".l");

				flag = flag_cache.get( cc_key );

				if ( flag == null ){

					InputStream is = fp.getCountryFlagForISO3166Code( cc, small?0:1 );

					if ( is != null ){

						try{
							Display display = Display.getDefault();

							flag = new Image( display, is);

						}finally{

							is.close();
						}
					}else{

						flag = flag_none;
					}

					flag_cache.put( cc_key, flag );
				}

			}catch( Throwable e ){

			}
		}

		if ( flag == flag_none ){

			return( null );
		}

		return( flag );
	}



	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display, SWT.SHELL_TRIM);
		shell.setLayout(new FillLayout(SWT.VERTICAL));

		final Label label = new Label(shell, SWT.BORDER);

		final Text text = new Text(shell, SWT.BORDER);
		text.addModifyListener(new ModifyListener() {

			@Override
			public void modifyText(ModifyEvent e) {
				Image pathIcon = getPathIcon(text.getText(), false, false);
				label.setImage(pathIcon);
			}
		});

		shell.open();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
}