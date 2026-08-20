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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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
import com.biglybt.core.util.AESemaphore;
import com.biglybt.core.util.AsyncDispatcher;
import com.biglybt.core.util.ByteFormatter;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.core.util.HostNameToIPResolver;
import com.biglybt.core.util.SHA1Simple;
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

	private static final AsyncDispatcher		async_icon_dispatcher = new AsyncDispatcher( "FileIconFetch" );

		// files with a lookup in flight, mapped to whoever wants telling when it
		// lands. a cell that has already painted won't ask for the icon again
		// (its text hasn't changed), so it has to be nudged to repaint - same
		// approach ColumnThumbAndName uses for async thumbnails

	private static final Map<String,java.util.List<Consumer<Image>>>	async_icon_pending = new HashMap<>();

		// per-file icons are shared by content: a path maps to a content key and
		// identical icons (the common case - most .exe files carry one of a
		// handful of installer icons) resolve to a single Image. the path->content
		// map is bounded so a library with millions of files can't accumulate
		// millions of path strings

	private static final int	PER_FILE_CACHE_MAX = 512;

		// marks a file whose icon lookup came back empty, so a failure doesn't
		// have every repaint queue the lookup again; the entry ages out of the
		// map normally, which lets a file that has become reachable retry

	private static final String	PER_FILE_NONE = "";

	private static final Map<String,String>	per_file_content_keys =
		Collections.synchronizedMap(
			new LinkedHashMap<String,String>( 128, 0.75f, true )
			{
				@Override
				protected boolean
				removeEldestEntry(
					Map.Entry<String,String> eldest )
				{
					return( size() > PER_FILE_CACHE_MAX );
				}
			});

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
	
	public static Image 
	getIconFromExtension(
		File file, 
		String ext, 
		boolean bBig,
		boolean minifolder) 
	{
		return( getIconFromExtension( file, ext, bBig, minifolder, null ));
	}

	public static Image 
	getIconFromExtension(
		File file, 
		String ext, 
		boolean bBig,
		boolean minifolder,
		Consumer<Image> icon_listener ) 
	{
		Image image = null;

		try {
				// files whose extension is in noCacheExtList (e.g. .exe) can carry
				// a per-file embedded icon, so they must not share a single icon
				// cached under the extension key. use a per-file key and fetch the
				// icon asynchronously to keep file I/O off the UI thread.

			boolean per_file_icon = false;

				// ignore_icon_exts is the user's "don't ask the shell about these"
				// list; per-file lookups must honour it too

			if ( !ignore_icon_exts.contains( ext.toLowerCase( Locale.US ))){

				for ( int i = 0; i < noCacheExtList.length; i++ ){
					if ( noCacheExtList[i].equalsIgnoreCase( ext )){
						per_file_icon = true;
						break;
					}
				}
			}

			String ext_key = "osicon" + ext;

			if ( bBig ) ext_key += "-big";
			if ( minifolder ) ext_key += "-fold";



			if ( per_file_icon ){

				String file_key = file.getAbsolutePath();

				if ( bBig ) file_key += "-big";
				if ( minifolder ) file_key += "-fold";

				String content_key = per_file_content_keys.get( file_key );


				if ( content_key != null ){

					if ( !content_key.isEmpty()){

						image = ImageLoader.getInstance().getImage( content_key );

						if ( ImageLoader.isRealImage( image )){
							return( image );
						}

						ImageLoader.getInstance().releaseImage( content_key );

						per_file_content_keys.remove( file_key );

						scheduleAsyncIconFetch( file, file_key, bBig, minifolder, icon_listener );
					}
				}else{

					scheduleAsyncIconFetch( file, file_key, bBig, minifolder, icon_listener );
				}


					// return the shared extension icon until the per-file one arrives

				image = ImageLoader.getInstance().getImage( ext_key );

				if ( ImageLoader.isRealImage( image )){
					return( image );
				}

				ImageLoader.getInstance().releaseImage( ext_key );

				Program program = Program.findProgram( ext );

				if ( program != null ){

					ImageData id = program.getImageData();

					if ( id != null ){

						image = new Image( Display.getDefault(), id );
						if ( !bBig ) image = force16height( image );
						if ( minifolder ) image = minifolderize( file.getParent(), image, bBig );
						ImageLoader.getInstance().addImageNoDipose( ext_key, image );
						return( image );
					}
				}

				return( ImageLoader.getInstance().getImage( minifolder ? "folder" : "transparent" ));
			}

			String key = ext_key;

			image = ImageLoader.getInstance().getImage(key);
			if (ImageLoader.isRealImage(image)) {
				return image;
			}

			ImageLoader.getInstance().releaseImage(key);
			image = null;

			if ( Utils.isFileResponding( file )){

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

		// resolve a per-file icon without blocking the interface: the (potentially
		// slow) file existence check runs on a background thread, the icon lookup
		// itself is then handed to the SWT thread as getFileIcon requires

	private static void
	scheduleAsyncIconFetch(
		final File		file,
		final String	file_key,
		final boolean	bBig,
		final boolean	minifolder,
		final Consumer<Image>	listener )
	{
		synchronized( async_icon_pending ){

			java.util.List<Consumer<Image>> waiting = async_icon_pending.get( file_key );

			if ( waiting != null ){

					// a lookup is already in flight for this file; just join it

				if ( listener != null ){

					waiting.add( listener );
				}

				return;
			}

			waiting = new java.util.ArrayList<>();

			if ( listener != null ) waiting.add( listener );

			async_icon_pending.put( file_key, waiting );
		}

		async_icon_dispatcher.dispatch(()->{
			
			Image[] result = { null };

			try{

				boolean reachable = Utils.fileExistsWithTimeout( file );


				if ( !reachable ){

					per_file_content_keys.put( file_key, PER_FILE_NONE );

					return;
				}

					// getFileIcon has to run on the SWT thread; block this background
					// thread until it has done so, which leaves the dispatcher
					// throttling the lookups one at a time instead of flooding the
					// SWT queue with thousands of them
											
				Utils.execSWTThread(()->{

					Image icon = null;

					if ( Constants.isWindows ){
						try{
							Class<?> cls = Class.forName( "com.biglybt.ui.swt.win32.Win32UIEnhancer" );
							Method m = cls.getMethod( "getFileIcon", new Class[]{ File.class, boolean.class });
							icon = (Image)m.invoke( null, new Object[]{ file, bBig });
						}catch( Throwable e ){
						}
					}else if ( Constants.isOSX ){
						try{
							Class<?> cls = Class.forName( "com.biglybt.ui.swt.osx.CocoaUIEnhancer" );
							Method m = cls.getMethod( "getFileIcon", new Class[]{ String.class, int.class });
							icon = (Image)m.invoke( null, new Object[]{ file.getAbsolutePath(), (int)(bBig ? 128 : 16) });
						}catch( Throwable e ){
						}
					}

					if ( icon != null ){
						
						icon = cachePerFileIcon( file, file_key, icon, bBig, minifolder );

						synchronized( result ){
							
							result[0] = icon;
						}
					}else{

						per_file_content_keys.put( file_key, PER_FILE_NONE );
					}
				}, false );
				
			}catch( Throwable e ){

			}finally{

				java.util.List<Consumer<Image>> waiting;

				synchronized( async_icon_pending ){

					waiting = async_icon_pending.remove( file_key );
				}

				if ( waiting != null ){

					Image image;
					
					synchronized( result ){
						
						image = result[0];
					}
					
					for ( Consumer<Image> r: waiting ){

						try{
							r.accept( image );

						}catch( Throwable e ){

							Debug.out( e );
						}
					}
				}
			}
		});
	}

		// most files of a given type carry one of a handful of icons, so key the
		// cached Image by icon content: a thousand installers sharing an icon end
		// up sharing a single Image rather than a thousand copies

	private static Image
	cachePerFileIcon(
		File		file,
		String		file_key,
		Image		icon,
		boolean		bBig,
		boolean		minifolder )
	{
		boolean	cached = false;

		try{
			ImageData data = icon.getImageData();

				// key on the pixels plus the geometry, so two icons that happen to
				// share a byte pattern at different sizes or depths don't collide

			String content_key =
				"osicon-pfc:" + ByteFormatter.encodeString( new SHA1Simple().calculateHash( data.data )) +
				"-" + data.width + "x" + data.height + "x" + data.depth;

			if ( bBig ) content_key += "-big";
			if ( minifolder ) content_key += "-fold";

			Image existing = ImageLoader.getInstance().getImage( content_key );
			
			boolean already_held = ImageLoader.isRealImage( existing );

				// the lookup above takes a reference either way; this method hands
				// the Image to nobody, so drop it again

			ImageLoader.getInstance().releaseImage( content_key );

			if ( already_held ){

					// an identical icon is already cached, so drop this copy and
					// point the file at the shared one

				per_file_content_keys.put( file_key, content_key );

				return( existing );
			}

			if ( !bBig ) icon = force16height( icon );
			if ( minifolder ) icon = minifolderize( file.getParent(), icon, bBig );

			ImageLoader.getInstance().addImageNoDipose( content_key, icon );

			cached = true;


			per_file_content_keys.put( file_key, content_key );

			return( icon );
			
		}catch( Throwable e ){

			Debug.out( e );

			return( icon );
			
		}finally{

			if ( !cached && icon != null && !icon.isDisposed()){

				icon.dispose();
			}
		}
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
	public static Image 
	getPathIcon(
		String		path,
		Boolean		isFile,
		boolean		bBig,
		boolean		minifolder) 
	{
		return( getPathIcon( path, isFile, bBig, minifolder, null ));
	}

		// icon_listener is run once an asynchronously resolved per-file icon has
		// landed in the cache. a table cell that has already painted won't ask
		// again on its own, so it uses this to invalidate itself and repaint.

	public static Image 
	getPathIcon(
		String			path, 
		Boolean			isFile,
		boolean			bBig,
		boolean			minifolder,
		Consumer<Image>	icon_listener ) 
	{
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
			if ( isFile==null?Utils.isDirectoryWithTimeout( file ):!isFile){
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
						return getIconFromExtension(file, ext, bBig, minifolder, icon_listener);

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

			bDeleteFile = !Utils.fileExistsWithTimeout(file);
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

				if (bDeleteFile && file != null && Utils.fileExistsWithTimeout( file )) {
					file.delete();
				}
				return image;
			}
		} catch (Exception e) {
			//Debug.printStackTrace(e);
		}

		if (bDeleteFile && file != null && Utils.fileExistsWithTimeout( file )) {
			file.delete();
		}

		// Possible scenario: Method call before file creation
		String ext = FileUtil.getExtension(path);
		if (ext.length() == 0) {
			return ImageLoader.getInstance().getImage("folder");
		}

		return getIconFromExtension(file, ext, bBig, minifolder, icon_listener);
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
				Image pathIcon = getPathIcon(text.getText(), null, false, false);
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