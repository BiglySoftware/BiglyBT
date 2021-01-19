/*
 * Created on Jun 7, 2006 2:31:26 PM
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
package com.biglybt.ui.swt.imageloader;

import java.io.*;
import java.net.MalformedURLException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.util.*;
import com.biglybt.ui.skin.SkinProperties;
import com.biglybt.ui.skin.SkinPropertiesImpl;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.Colors;
import com.biglybt.ui.utils.ImageBytesDownloader;

/**
 * Loads images from a skinProperty object.
 * <p>
 * Will look for special suffixes (over, down, disabled) and try to
 * load resources using base key and suffix. ie. loadImage("foo-over") when
 * foo=image.png, will load image-over.png
 * <p>
 * Will also create own disabled images if base image found and no disabled
 * image found.  Disabled opacity can be set via imageloader.disabled-opacity
 * key
 *
 * @author TuxPaper
 * @created Jun 7, 2006
 *
 */
public class ImageLoader
	implements AEDiagnosticsEvidenceGenerator
{
	private static ImageLoader instance;

	private static final boolean DEBUG_UNLOAD = false;

	private static final boolean DEBUG_REFCOUNT = false;

	private static final int GC_INTERVAL = 60 * 1000;

	private final String[] sSuffixChecks = {
		"-over",
		"-down",
		"-disabled",
		"-selected",
		"-gray",
	};
	private final TimerEventPeriodic periodicEvent;

	private Display display;

	public static Image noImage;
	public static Image nullImage;

	private final ConcurrentHashMap<String, ImageLoaderRefInfo> _mapImages;

	private final ArrayList<String> notFound;

	private CopyOnWriteArrayList<SkinProperties> skinProperties;

	//private final ClassLoader classLoader;

	private int disabledOpacity;

	private Set<String>		cached_resources = new HashSet<>();

	private File cache_dir = new File(SystemProperties.getUserPath(), "cache" );


	public static synchronized ImageLoader getInstance() {
		if (instance == null) {
			if (Utils.isDisplayDisposed()) {
				throw new SWTException("Calling getInstance after display disposed");
			}
			instance = new ImageLoader(Display.getDefault(), null);
			// always add az2 icons to instance
			SkinPropertiesImpl skinProperties = new SkinPropertiesImpl(
					ImageRepository.class.getClassLoader(),
					"com/biglybt/ui/icons/", "icons.properties");
			instance.addSkinProperties(skinProperties);
		}
		return instance;
	}

	public static void disposeInstance() {
		if (instance != null) {
			instance.dispose();
		}
		instance = null;
		if (noImage != null) {
			noImage.dispose();
			noImage = null;
		}
		if (nullImage != null) {
			nullImage.dispose();
			nullImage = null;
		}
	}

	public void dispose() {
		// Dispose of images in case some code stored the image in a static variable
		// Image.isDisposed() does not get set to true when the Display is disposed,
		// but trying to reference it later (with a new Display) will result in
		// Device is Disposed
		List<ImageLoaderRefInfo> values = new ArrayList<>(_mapImages.values());
		_mapImages.clear();
		for (ImageLoaderRefInfo value : values) {
			Image[] images = value.getImages();
			if (images != null) {
				for (Image image : images) {
					image.dispose();
				}
			}
		}
		if (periodicEvent != null && !periodicEvent.isCancelled()) {
			periodicEvent.cancel();
		}
	}

	public ImageLoader(/*ClassLoader classLoader,*/Display display,
			SkinProperties skinProperties) {
		//this.classLoader = classLoader;

		File[]	files = cache_dir.listFiles();

		if ( files != null ){
			for (File f: files ){
				String	name = f.getName();
				if ( name.endsWith( ".ico" )){
					cached_resources.add( name );
				}
			}
		}

		_mapImages = new ConcurrentHashMap<>();
		notFound = new ArrayList<>();
		this.display = display;
		this.skinProperties = new CopyOnWriteArrayList<>();
		addSkinProperties(skinProperties);

		AEDiagnostics.addWeakEvidenceGenerator(this);
		if (GC_INTERVAL > 0) {
			periodicEvent = SimpleTimer.addPeriodicEvent("GC_ImageLoader", GC_INTERVAL,
					new TimerEventPerformer() {
						@Override
						public void perform(TimerEvent event) {
							if (!collectGarbage()) {
								event.cancel();
							}
						}
					});
		}
	}

	/*
	private Image loadImage(Display display, String key) {
		for (SkinProperties sp : skinProperties) {
			String value = sp.getStringValue(key);
			if (value != null) {
				return loadImage(display, sp.getClassLoader(), value, key);
			}
		}
		return loadImage(display, null, null, key);
	}
	*/

	private Image[] findResources(String sKey) {
		if (Collections.binarySearch(notFound, sKey) >= 0) {
			return null;
		}

		for (int i = 0; i < sSuffixChecks.length; i++) {
			String sSuffix = sSuffixChecks[i];

			if (sKey.endsWith(sSuffix)) {
				//System.out.println("YAY " + sSuffix + " for " + sKey);
				String sParentName = sKey.substring(0, sKey.length() - sSuffix.length());
				/*
				Image[] images = getImages(sParentName);
				if (images != null && images.length > 0 && isRealImage(images[0])) {
					return images;
				}
				*/
				/**/
				String[] sParentFiles = null;
				ClassLoader cl = null;
				for (SkinProperties sp : skinProperties) {
					sParentFiles = sp.getStringArray(sParentName);
					if (sParentFiles != null) {
						cl = sp.getClassLoader();
						break;
					}
				}
				if (sParentFiles != null) {
					boolean bFoundOne = false;
					Image[] images = parseValuesString(cl, sKey, sParentFiles, sSuffix);
					if (images != null) {
						for (int j = 0; j < images.length; j++) {
							Image image = images[j];
							if (isRealImage(image)) {
								bFoundOne = true;
								break;
							}
						}
						if (!bFoundOne) {
							for (int j = 0; j < images.length; j++) {
								Image image = images[j];
								if (isRealImage(image)) {
									image.dispose();
								}
							}
						} else {
							return images;
						}
					}
				} else {
					// maybe there's another suffix..
					Image[] images = findResources(sParentName);
					if (images != null) {
						return images;
					}
				}
				/**/
			}
		}

		int i = Collections.binarySearch(notFound, sKey) * -1 - 1;
		if (i >= 0) {
			notFound.add(i, sKey);
		}
		return null;
	}

	/**
	 * @param values
	 * @param suffix
	 * @return
	 *
	 * @since 3.1.1.1
	 */
	private Image[] parseValuesString(ClassLoader cl, String sKey,
			String[] values, String suffix) {
		
		if ( values.length == 1 && values[0].equals( "null" )){
			
			return( new Image[]{ getNullImage() });
		}
		
		Image[] images = null;

		boolean scale = true;

		int splitX = 0;
		int locationStart = 0;
		int useIndex = -1; // all
		if (values.length > 1) {
			if (values[0].equals("multi") && values.length > 2) {
				splitX = Integer.parseInt(values[1]);
				locationStart = 2;
			} else if (values[0].equals("multi-index") && values.length > 3) {
				splitX = Integer.parseInt(values[1]);
				useIndex = Integer.parseInt(values[2]);
				locationStart = 3;
			} else if (values[0].equals("noscale")) {
				scale = false;
				locationStart = 1;
			}
		}

		if (locationStart == 0 || splitX <= 0) {
			images = new Image[values.length - locationStart];
			for (int i = 0; i < images.length; i++) {
				String value = values[i + locationStart];
				int index = value.lastIndexOf('.');
				if (index > 0) {
					String sTryFile = value.substring(0, index) + suffix
							+ value.substring(index);
					images[i] = loadImage(display, cl, sTryFile, sKey);

					if (images[i] == null) {
						sTryFile = value.substring(0, index) + suffix.replace('-', '_')
								+ value.substring(index);
						images[i] = loadImage(display, cl, sTryFile, sKey);
					}
				}

				if (images[i] == null) {
					images[i] = getNoImage(sKey);
				}
			}
		} else {
			Image image = null;

			String image_key = null;	// if image in repository then this will be non-null

			String origFile = values[locationStart];
			int index = origFile.lastIndexOf('.');

			if (index > 0) {
				if (useIndex == -1) {
					String sTryFile = origFile.substring(0, index) + suffix
							+ origFile.substring(index);
					image = loadImage(display, cl, sTryFile, sKey);

						// not in repo

					if (image == null) {
						sTryFile = origFile.substring(0, index) + suffix.replace('-', '_')
								+ origFile.substring(index);
						image = loadImage(display, cl, sTryFile, sKey);

							// not in repo
					}
				} else {
					String sTryFile = origFile.substring(0, index) + suffix
							+ origFile.substring(index);

						// check the cache to see if full image is in there

					image = getImageFromMap( sTryFile );

					if ( image == null ){

						image = loadImage(display, cl, sTryFile, sTryFile);

						if ( isRealImage(image)){

							image_key = sTryFile;

							addImage(image_key, image);

						} else if (sTryFile.matches(".*[-_]disabled.*")) {

							String sTryFileNonDisabled = sTryFile.replaceAll("[-_]disabled", "");

							image = getImageFromMap(sTryFileNonDisabled);

							if (!isRealImage(image)) {

								image = loadImage(display, cl, sTryFileNonDisabled,
										sTryFileNonDisabled);

								if ( isRealImage(image)) {

									addImage(sTryFileNonDisabled, image);

								}
							}

							if ( isRealImage(image)){

								image = fadeImage(image);

								image_key = sTryFile;

								addImage(image_key, image);

								releaseImage(sTryFileNonDisabled);
							}
						}
					}else{

						image_key = sTryFile;
					}
				}
			}

			if ( !isRealImage(image)) {

				String	temp_key = sKey + "-[multi-load-temp]";

				image = getImageFromMap( temp_key );

				if ( isRealImage(image)){

					image_key = temp_key;

				}else{

					image = loadImage(display, cl, values[locationStart], sKey);

					if ( isRealImage(image)) {

						image_key = temp_key;

						addImage( image_key, image );
					}
				}
			}

			if (isRealImage(image)) {
				Rectangle bounds = image.getBounds();
				if (useIndex == -1) {
					images = new Image[(bounds.width + splitX - 1) / splitX];
					for (int i = 0; i < images.length; i++) {
						Image imgBG = Utils.createAlphaImage(display, splitX, bounds.height,
								(byte) 0);
						int pos = i * splitX;
						try {
							images[i] = Utils.blitImage(display, image, new Rectangle(pos, 0,
									Math.min(splitX, bounds.width - pos), bounds.height), imgBG,
									new Point(0, 0));
						} catch (Exception e) {
							Debug.out(e);
						}
						imgBG.dispose();
					}
				} else {
					images = new Image[1];
					Image imgBG = Utils.createAlphaImage(display, splitX, bounds.height, (byte) 0);
					try {
						int pos = useIndex * splitX;
						//
						images[0] = Utils.blitImage(display, image, new Rectangle(pos, 0,
								Math.min(splitX, bounds.width - pos), bounds.height), imgBG,
								new Point(0, 0));
					} catch (Exception e) {
						Debug.out(e);
					}
					imgBG.dispose();
				}

				if ( image_key != null ){

					releaseImage(image_key);

				}else{

					image.dispose();
				}
			}
		}

		return images;
	}

	private Image loadImage(Display display, ClassLoader cl, String res,
			String sKey) {
		Image img = null;

		//System.out.println("LoadImage " + sKey + " - " + res);
		if (res == null) {
			for (int i = 0; i < sSuffixChecks.length; i++) {
				String sSuffix = sSuffixChecks[i];

				if (sKey.endsWith(sSuffix)) {
					//System.out.println("Yay " + sSuffix + " for " + sKey);
					String sParentName = sKey.substring(0, sKey.length()
							- sSuffix.length());
					String sParentFile = null;
					for (SkinProperties sp : skinProperties) {
						sParentFile = sp.getStringValue(sParentName);
						if (sParentFile != null) {
							if (cl == null) {
								cl = sp.getClassLoader();
							}
							break;
						}
					}
					if (sParentFile != null) {
						int index = sParentFile.lastIndexOf('.');
						if (index > 0) {
							String sTryFile = sParentFile.substring(0, index) + sSuffix
									+ sParentFile.substring(index);
							img = loadImage(display, cl, sTryFile, sKey);

							if (img != null) {
								break;
							}

							sTryFile = sParentFile.substring(0, index)
									+ sSuffix.replace('-', '_') + sParentFile.substring(index);
							img = loadImage(display, cl, sTryFile, sKey);

							if (img != null) {
								break;
							}
						}
					}
				}
			}
		}

		if (img == null) {
			try {
				if (cl != null && res != null ) {
					int deviceZoom = Utils.getDeviceZoom();
					InputStream is = cl.getResourceAsStream(res);
					InputStream is2 = null;
					if (deviceZoom > 100) {
						int i = res.lastIndexOf("/");
						if (i >= 0) {
							String bigRes = res.substring(0, i) + "/2x" + res.substring(i);
							is2 = cl.getResourceAsStream(bigRes);
						}
					}
					if (is != null) {
						try{
							ImageData imageData = new ImageData(is);
							if (is2 != null) {
								ImageData imageData2 = new ImageData(is2);
								img = new Image(display, (ImageDataProvider) zoom -> {
									if (zoom == 100) {
										return imageData;
									}
									return zoom == 200 ? imageData2 : null;
								});
							} else {
								img = new Image(display, imageData);
							}
							//	System.out.println("Loaded image from " + res + " via " + Debug.getCompressedStackTrace());

						}finally{
							is.close();
						}
					}
				}

				if (img == null) {
					// don't do on sKey.endsWith("-disabled") because caller parseValueString
					// requires a failure so it can retry with _disabled.  If that fails,
					// we'll get here (stupid, I know)
					if ( res != null && res.contains("_disabled.")) {
						String id = sKey.substring(0, sKey.length() - 9);
						Image imgToFade = getImage(id);
						if (isRealImage(imgToFade)) {
							img = fadeImage(imgToFade);
						}
						releaseImage(id);
					}else if (sKey.endsWith("-gray")) {
						String id = sKey.substring(0, sKey.length() - 5);
						Image imgToGray = getImage(id);
						if (isRealImage(imgToGray)) {
							img = new Image( display, imgToGray, SWT.IMAGE_GRAY );
						}
						releaseImage(id);
					}
					//System.err.println("ImageRepository:loadImage:: Resource not found: " + res);
				}
			} catch (Throwable e) {
				System.err.println("ImageRepository:loadImage:: Resource not found: "
						+ res + "\n" + e);
				e.printStackTrace();
			}
		}

		return img;
	}

	private Image fadeImage(Image imgToFade) {
		ImageData imageData = imgToFade.getImageData();
		Image img;
		// decrease alpha
		if (imageData.alphaData != null) {
			if (disabledOpacity == -1) {
				for (int i = 0; i < imageData.alphaData.length; i++) {
					imageData.alphaData[i] = (byte) ((imageData.alphaData[i] & 0xff) >> 3);
				}
			} else {
				for (int i = 0; i < imageData.alphaData.length; i++) {
					imageData.alphaData[i] = (byte) ((imageData.alphaData[i] & 0xff)
							* disabledOpacity / 100);
				}
			}
			img = new Image(display, imageData);
		} else {
			Rectangle bounds = imgToFade.getBounds();
			Image bg = Utils.createAlphaImage(display, bounds.width,
					bounds.height, (byte) 0);

			img = Utils.renderTransparency(display, bg, imgToFade,
					new Point(0, 0), disabledOpacity == -1 ? 64
							: disabledOpacity * 255 / 100);
			bg.dispose();
		}
		return img;
	}

	public void unLoadImages() {
		if (DEBUG_UNLOAD) {
			for (String key : _mapImages.keySet()) {
				Image[] images = _mapImages.get(key).getImages();
				if (images != null) {
					for (int i = 0; i < images.length; i++) {
						Image image = images[i];
						if (isRealImage(image)) {
							System.out.println("dispose " + image + ";" + key);
							image.dispose();
						}
					}
				}
			}
		} else {
			for (ImageLoaderRefInfo imageInfo : _mapImages.values()) {
				Image[] images = imageInfo.getImages();
				if (images != null) {
					for (int i = 0; i < images.length; i++) {
						Image image = images[i];
						if (isRealImage(image)) {
							image.dispose();
						}
					}
				}
			}
		}
	}

	private ImageLoaderRefInfo
	getRefInfoFromImageMap(
		String		key )
	{
		return( _mapImages.get(key));
	}

	private void
	putRefInfoToImageMap(
		String				key,
		ImageLoaderRefInfo	info )
	{
		ImageLoaderRefInfo existing = _mapImages.put( key, info );

		if ( existing != null ){

			Image[] images = existing.getImages();
			if ( images != null && images.length > 0 ){

				Debug.out( "P: existing found! " + key + " -> " + existing.getString());
			}
		}
	}

	private ImageLoaderRefInfo
	putIfAbsentRefInfoToImageMap(
		String				key,
		ImageLoaderRefInfo	info )
	{
		ImageLoaderRefInfo x = _mapImages.putIfAbsent( key, info );

		if ( x != null ){

			Image[] images = x.getImages();
			if ( images != null && images.length > 0 ){

				Debug.out( "PIA: existing found! " + key + " -> " + x.getString());
			}
		}

		return( x );
	}

	protected Image getImageFromMap(String sKey) {
		Image[] imagesFromMap = getImagesFromMap(sKey);
		if (imagesFromMap.length == 0) {
			return null;
		}
		return imagesFromMap[0];
	}

	protected Image[] getImagesFromMap(String sKey) {
		if (sKey == null) {
			return new Image[0];
		}

		ImageLoaderRefInfo imageInfo = getRefInfoFromImageMap( sKey );
		if (imageInfo != null && imageInfo.getImages() != null) {
			imageInfo.addref();
			if (DEBUG_REFCOUNT) {
				logRefCount( sKey, imageInfo, true );
			}
			return imageInfo.getImages();
		}

		return new Image[0];
	}

	public Image[] getImages(String sKey) {
		if (sKey == null) {
			return new Image[0];
		}

		if (!Utils.isThisThreadSWT()) {
			Debug.out("getImages called on non-SWT thread");
			return new Image[0];
		}

		// ugly hack to show sidebar items that are disabled
		// note this messes up refcount (increments but doesn't decrement)
		if (sKey.startsWith("http://") && sKey.endsWith("-gray")) {
			sKey = sKey.substring(0, sKey.length() - 5);
		}

		ImageLoaderRefInfo imageInfo = getRefInfoFromImageMap(sKey);
		if (imageInfo != null && imageInfo.getImages() != null) {
			imageInfo.addref();
			if (DEBUG_REFCOUNT) {
				logRefCount( sKey, imageInfo, true );
			}
			return imageInfo.getImages();
		}

		Image[] images;
		String[] locations = null;
		ClassLoader cl = null;
		for (SkinProperties sp : skinProperties) {
			locations = sp.getStringArray(sKey);
			if (locations != null && locations.length > 0) {
				cl = sp.getClassLoader();
				break;
			}
		}
		//		System.out.println(sKey + "=" + properties.getStringValue(sKey)
		//				+ ";" + ((locations == null) ? "null" : "" + locations.length));
		if (locations == null || locations.length == 0) {
			images = findResources(sKey);

			if (images == null) {
					images = new Image[0];
			}

			for (int i = 0; i < images.length; i++) {
				if (images[i] == null) {
					images[i] = getNoImage( sKey );
				}
			}
		} else {
			images = parseValuesString(cl, sKey, locations, "");
		}

		ImageLoaderRefInfo info = new ImageLoaderRefInfo(images);
		putRefInfoToImageMap(sKey, info );
		if (DEBUG_REFCOUNT) {
			logRefCount( sKey, info, true );
		}

		return images;
	}


	public Image
	getImage( String sKey )
	{
			// hack to allow main color to be replaced by another in an image

		int	pos = sKey.indexOf( '#' );

		if ( pos == -1 ){

			Image res = getImageSupport( sKey );
			
			if ( res == nullImage ){
				
				return( null );
				
			}else{
				
				return( res );
			}

		}else{

			ImageLoaderRefInfo existing = getRefInfoFromImageMap( sKey );

			if ( existing != null ){

					// already computed and cached

				return( getImageSupport(sKey));
			}

			String basisKey = sKey.substring( 0,  pos );

			Image basis = getImageSupport( basisKey);

			Image result = null;

			if ( isRealImage( basis )){

				try{
					long l = Long.parseLong(sKey.substring(pos+1), 16);

					int	to_red 		= (int)((l >> 16) & 0xff);
					int to_green 	= (int)((l >> 8) & 0xff);
					int	to_blue		= (int)(l & 0xff);

					ImageData original_id = basis.getImageData();

					Image tempImg = new Image( basis.getDevice(), basis.getBounds());

					GC tempGC = new GC( tempImg );

					tempGC.drawImage( basis, 0, 0 );

					tempGC.dispose();

					ImageData id = tempImg.getImageData();

					tempImg.dispose();

					int[] pixels = new int[id.width*id.height];

					id.getPixels( 0, 0, pixels.length, pixels, 0 );

					PaletteData palette = id.palette;

					// should be direct!

					if ( palette.isDirect ){

						int redMask 	= palette.redMask;
						int greenMask 	= palette.greenMask;
						int blueMask 	= palette.blueMask;
						int redShift 	= palette.redShift;
						int greenShift 	= palette.greenShift;
						int blueShift 	= palette.blueShift;

						int[] rgbs = new int[id.width*id.height];

						for ( int i=0;i<pixels.length;i++){

							int pixel = pixels[i];

							int red = pixel & redMask;
							red = (redShift < 0) ? red >>> -redShift : red << redShift;
							int green = pixel & greenMask;
							green = (greenShift < 0) ? green >>> -greenShift : green << greenShift;
							int blue = pixel & blueMask;
							blue = (blueShift < 0) ? blue >>> -blueShift : blue << blueShift;

							rgbs[i] = (red<<16)|(green<<8)|blue;
						}

						Arrays.sort( rgbs );

						int	curr 	= -1;
						int len		= 0;

						int	max_len = -1;
						int max_rgb	= 0;

						for ( int i=0;i<rgbs.length;i++){

							int x = rgbs[i];

							if ( x == 0 || x == 0x00ffffff ){
								continue;
							}

							if ( x == curr ){
								len++;
								if ( len > max_len ){
									max_rgb = x;
									max_len	= len;
								}
							}else{
								curr = x;
								len = 1;
							}
						}

						to_red = (redShift < 0) ? to_red << -redShift : to_red >>> -redShift;
						to_red = to_red & redMask;
						to_green = (greenShift < 0) ? to_green << -greenShift : to_green >>> -greenShift;
						to_green = to_green & greenMask;
						to_blue = (blueShift < 0) ? to_blue << -blueShift : to_blue >>> -blueShift;
						to_blue = to_blue & blueMask;

						int to_rgb = to_red | to_green | to_blue;


						byte[] 	alphaData 		= null;

						if ( original_id.alphaData != null ){

							id.alphaData	= original_id.alphaData;

						}else if ( original_id.transparentPixel >= 0 ){

							alphaData = new byte[pixels.length];

							Arrays.fill( alphaData, (byte)0xff );

							id.alphaData = alphaData;

							int[] original_pixels = new int[id.width*id.height];

							original_id.getPixels( 0, 0, original_pixels.length, original_pixels, 0 );

							for ( int i=0;i<original_pixels.length;i++){

								if ( original_pixels[i] == original_id.transparentPixel ){

									alphaData[i] = 0;
								}
							}
						}

						for ( int i=0;i<pixels.length;i++){

							int pixel = pixels[i];

							int red = pixel & redMask;
							red = (redShift < 0) ? red >>> -redShift : red << redShift;
							int green = pixel & greenMask;
							green = (greenShift < 0) ? green >>> -greenShift : green << greenShift;
							int blue = pixel & blueMask;
							blue = (blueShift < 0) ? blue >>> -blueShift : blue << blueShift;

							int rgb = (red<<16)|(green<<8)|blue;

							if ( rgb == max_rgb ){

								pixels[i] = to_rgb;
							}
						}

						id.setPixels( 0, 0, pixels.length, pixels, 0 );

						result = new Image( basis.getDevice(), id );
					}
				}catch( Throwable e ){

					Debug.out( e );

				}finally{

					releaseImage( basisKey );
				}
			}

			if ( result == null ){

				result = getNoImage( sKey );
			}

			ImageLoaderRefInfo info = new ImageLoaderRefInfo(new Image[]{ result });

			putRefInfoToImageMap(sKey, info );

			if (DEBUG_REFCOUNT) {

				logRefCount( sKey, info, true );
			}

			return( result );
		}
	}

	private Image getImageSupport(String sKey) {
		Image[] images = getImages(sKey);
		if (images == null || images.length == 0 || images[0] == null || images[0].isDisposed()) {
			return getNoImage( sKey );
		}
		return images[0];
	}

	public long releaseImage(String sKey) {
		if (sKey == null) {
			return 0;
		}
		ImageLoaderRefInfo imageInfo = getRefInfoFromImageMap(sKey);
		if (imageInfo != null) {
			imageInfo.unref();
			if (false && imageInfo.getRefCount() < 0) {
				Image[] images = imageInfo.getImages();
				System.out.println("ImageLoader refcount < 0 for "
						+ sKey
						+ " by "
						+ Debug.getCompressedStackTrace()
						+ "\n  "
						+ (images == null ? "null"
								: ("" + images.length + ";" + (images.length == 0 ? "0" : ""
										+ (images[0] == noImage)))));
			}
			if (DEBUG_REFCOUNT) {
				logRefCount( sKey, imageInfo, false );
			}
			return imageInfo.getRefCount();
			// TODO: cleanup?
		}
		return 0;
	}

	/**
	 * Adds image to repository.  refcount will be 1, or if key already exists,
	 * refcount will increase.
	 *
	 * @param key
	 * @param image
	 *
	 * @since 4.0.0.5
	 */
	public void addImage(String key, Image image) {
		if (!Utils.isThisThreadSWT()) {
			Debug.out("addImage called on non-SWT thread");
			return;
		}
		ImageLoaderRefInfo existing = putIfAbsentRefInfoToImageMap(key,
				new ImageLoaderRefInfo(image));
		if (existing != null) {
			// should probably fail if refcount > 0
			existing.setImages(new Image[] {
				image
			});
			existing.addref();
			if (DEBUG_REFCOUNT) {
				logRefCount( key, existing, true );
			}
		}
	}

	public void addImage(String key, Image[] images) {
		if (!Utils.isThisThreadSWT()) {
			Debug.out("addImage called on non-SWT thread");
			return;
		}
		ImageLoaderRefInfo existing = putIfAbsentRefInfoToImageMap(key,
				new ImageLoaderRefInfo(images));
		if (existing != null) {
			// should probably fail if refcount > 0
			existing.setImages(images);
			existing.addref();
			if (DEBUG_REFCOUNT) {
				logRefCount( key, existing, true );
			}
		}
	}

	private void
	logRefCount(
		String				key,
		ImageLoaderRefInfo	info,
		boolean				inc )
	{
		if (info.isNonDisposable()) {
			return;
		}

		if ( inc ){
			System.out.println("ImageLoader: ++ refcount to "
					+ info.getRefCount() + " for " + key + " via "
					+ Debug.getCompressedStackTraceSkipFrames(1));
		}else{
			System.out.println("ImageLoader: -- refcount to "
					+ info.getRefCount() + " for " + key + " via "
					+ Debug.getCompressedStackTraceSkipFrames(1));
		}
	}

	/*
	public void removeImage(String key) {
		// EEP!
		mapImages.remove(key);
	}
	*/

	public void addImageNoDipose(String key, Image image) {
		if (!Utils.isThisThreadSWT()) {
			Debug.out("addImageNoDispose called on non-SWT thread");
			return;
		}
		ImageLoaderRefInfo existing = putIfAbsentRefInfoToImageMap(key,
				new ImageLoaderRefInfo(image));
		if (existing != null) {
			existing.setNonDisposable();
			// should probably fail if refcount > 0
			existing.setImages(new Image[] {
				image
			});
			existing.addref();
			if (DEBUG_REFCOUNT) {
				logRefCount( key, existing, true );
			}
		}
	}

	public static Image getNoImage(){
		return( getNoImage( "explicit" ));
	}

	private static Image getNoImage( String key ) {
		/*
		if ( key != null ){
			System.out.println( "Missing image: " + key );
		}
		*/
		if (noImage == null) {
			Display display = Display.getDefault();
			final int SIZE = 10;
			noImage = new Image(display, SIZE, SIZE);
			GC gc = new GC(noImage);
			gc.setBackground(Colors.getSystemColor(display, SWT.COLOR_YELLOW));
			gc.fillRectangle(0, 0, SIZE, SIZE);
			gc.setBackground(Colors.getSystemColor(display, SWT.COLOR_RED));
			gc.drawRectangle(0, 0, SIZE - 1, SIZE - 1);
			gc.dispose();
		}
		return noImage;
	}
	
	private static Image getNullImage() {

		if (nullImage == null) {
			Display display = Display.getDefault();
			final int SIZE = 1;
			nullImage = new Image(display, SIZE, SIZE);
		}
		return nullImage;
	}

	public boolean imageExists(String name) {
		// Do a quick check first which doesn't need releasing
		ImageLoaderRefInfo refInfoFromImageMap = getRefInfoFromImageMap(name);
		if (refInfoFromImageMap != null) {
			Image[] images = refInfoFromImageMap.getImages();
			if (images != null && images.length > 0 && isRealImage(images[0])) {
				return true;
			}
		}

		boolean exists = isRealImage(getImage(name));
		//if (exists) {	// getImage prety much always adds a ref for the 'name' so make sure
						// we do the corresponding unref here
			releaseImage(name);
		//}
		return exists;
	}

	public Image[] imageAdded_NoSWT(String name) {
		ImageLoaderRefInfo info = _mapImages.get(name);
		
		if ( info != null ){
			return( info.getImages());
		}
		
		return( null );
	}

	public boolean imageAdded(String name) {
		Image[] images = getImages(name);
		boolean added = images != null && images.length > 0;
		releaseImage(name);
		return added;
	}

	public static boolean isRealImage(Image image) {
		if ( image == null || image.isDisposed() ){
			return( false );
		}
		if ( noImage != null ){
			return( image != noImage );
		}
		return( image != getNoImage( null ));
	}

	public int getAnimationDelay(String sKey) {
		for (SkinProperties sp : skinProperties) {
			int delay = sp.getIntValue(sKey + ".delay", -1);
			if (delay >= 0) {
				return delay;
			}
		}
		return 100;
	}

	public Image getUrlImage(String url, ImageDownloaderListener l) {
		return getUrlImage(url, null, l);
	}

	/**
	 * Get an {@link Image} from an url.  URL will be the key, which you
	 * can use later to {@link #releaseImage(String)}
	 */
	public Image getUrlImage(String url, final Point maxSize,
			final ImageDownloaderListener l) {
		if (!Utils.isThisThreadSWT()) {
			Debug.out("Called on non-SWT thread");
			return null;
		}
		if (l == null || url == null) {
			return null;
		}

		String imageKey = url;
		
		return( getUrlImageSupport( url, imageKey, maxSize, l ));
	}
	
	/**
	 * 
	 * @param file
	 * @param lastModified > 0 -> image key will include this for caching purposes
	 * @param maxSize		non-null -> image will be resized if required
	 * @param l
	 * @return
	 */
	
	public Image 
	getFileImage( 
		File 		file, 
		Point 		maxSize,
		final 		ImageDownloaderListener l) 
	{
		if (!Utils.isThisThreadSWT()) {
			
			Debug.out("Called on non-SWT thread");
			
			return null;
		}
		
		if (l == null || file == null) {
			return null;
		}
		
		try{
			String url = file.toURI().toURL().toExternalForm();
	
			String imageKey = url;
			
			long lastModified = file.lastModified();
			
			if ( lastModified > 0 ){
				
				imageKey += ";" + lastModified;
			}
			
			return( getUrlImageSupport( url, imageKey, maxSize, l ));
			
		}catch( MalformedURLException e ){
			
			Debug.out( e );
			
			return( null );
		}
	}
	
	private Image 
	getUrlImageSupport(
		String 						url, 
		String 						baseImageKey,
		Point  						maxSize,
		ImageDownloaderListener 	l) 
	{
		String sizedImageKey;
		
		if (maxSize == null) {
			sizedImageKey = baseImageKey;
		} else {
			sizedImageKey = maxSize.x + "x" + maxSize.y + ";" + baseImageKey;
		}
	
			// we can't use imageExists, getImage etc for these images as they are cached in the cache_dir in their
			// full size and then resized from this - those other methods don't know maxSize so can't do that
		
		ImageLoaderRefInfo refInfoFromImageMap = getRefInfoFromImageMap(sizedImageKey);
		if ( refInfoFromImageMap != null ){
			Image[] images = refInfoFromImageMap.getImages();
			if ( images == null || images.length == 0 ){
				return( null );
			}
			Image image = images[0];
			refInfoFromImageMap.addref();
			l.imageDownloaded(image, sizedImageKey, true);
			return image;
		}

		final String cache_key = baseImageKey.hashCode() + ".ico";

		final File cache_file = new File( cache_dir, cache_key );

		if ( cached_resources.contains( cache_key )){

			if ( cache_file.exists()){
				try {
					FileInputStream fis = new FileInputStream(cache_file);

					try {
						byte[] imageBytes = FileUtil.readInputStreamAsByteArray(fis);
						InputStream is = new ByteArrayInputStream(imageBytes);
						Image image = new Image(Display.getCurrent(), is);
						try {
							is.close();
						} catch (IOException e) {
						}
						if (maxSize != null) {
							Image newImage = resizeImageIfLarger(image, maxSize);
							if (newImage != null) {
								image.dispose();
								image = newImage;
							}
						}
						putRefInfoToImageMap(sizedImageKey, new ImageLoaderRefInfo(image));
						l.imageDownloaded(image, sizedImageKey, true);
						return image;
					} finally {
						fis.close();
					}
				} catch (Throwable e) {
					System.err.println(e.getMessage() + " for " + url + " at " + cache_file);
					//Debug.printStackTrace(e);
				}
			}else{

				cached_resources.remove( cache_key );
			}
		}

		ImageBytesDownloader.loadImage(url,
				new ImageBytesDownloader.ImageDownloaderListener() {
					@Override
					public void imageDownloaded(final byte[] imageBytes) {
						Utils.execSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
									// no synchronization here - might have already been
									// downloaded
								ImageLoaderRefInfo refInfoFromImageMap = getRefInfoFromImageMap(sizedImageKey);
								if ( refInfoFromImageMap != null ){
									Image[] images = refInfoFromImageMap.getImages();
									if ( images == null || images.length == 0 ){
										return;
									}
									Image image = images[0];
									refInfoFromImageMap.addref();
									l.imageDownloaded(image, sizedImageKey, true);
									return;
								}
								
								FileUtil.writeBytesAsFile(cache_file.getAbsolutePath(), imageBytes);
								
								cached_resources.add( cache_key );
								
								InputStream is = new ByteArrayInputStream(imageBytes);
								
								try {
									Image image = new Image(Display.getCurrent(), is);
									try {
										is.close();
									} catch (IOException e) {
									}
									if (maxSize != null) {
										Image newImage = resizeImageIfLarger(image, maxSize);
										if (newImage != null) {
											image.dispose();
											image = newImage;
										}
									}
									putRefInfoToImageMap(sizedImageKey, new ImageLoaderRefInfo(image));
									l.imageDownloaded(image, sizedImageKey, false);
								} catch (SWTException swte) {
									//  org.eclipse.swt.SWTException: Unsupported or unrecognized format
									System.err.println(swte.getMessage() + " for " + sizedImageKey + " at " + cache_file);
								}
							}
						});
					}
				});
		return null;
	}

	public Image resizeImageIfLarger(Image image, Point maxSize) {

		if (image == null || image.isDisposed()) {
			return null;
		}

		Rectangle bounds = image.getBounds();
		if (maxSize.y > 0 && bounds.height > maxSize.y) {
			int newX = bounds.width * maxSize.y / bounds.height;
			if (maxSize.x <= 0 || newX <= maxSize.x) {
				ImageData scaledTo = image.getImageData().scaledTo(newX, maxSize.y);
				Device device = image.getDevice();
				return new Image(device, scaledTo);
			}
		}
		if (maxSize.x > 0 && bounds.width > maxSize.x) {
			int newY = bounds.height * maxSize.x / bounds.width;
			ImageData scaledTo = image.getImageData().scaledTo(maxSize.x, newY);
			Device device = image.getDevice();
			return new Image(device, scaledTo);
		}
		return null;
	}

	public InputStream getImageStream(String sKey) {
		String[] locations = null;
		ClassLoader cl = null;
		for (SkinProperties sp : skinProperties) {
			locations = sp.getStringArray(sKey);
			if (locations != null && locations.length > 0) {
				cl = sp.getClassLoader();
				break;
			}
		}
		if (locations != null) {
			return cl.getResourceAsStream(locations[locations.length - 1]);
		}
		return null;
	}

	public static interface ImageDownloaderListener
	{
		public void imageDownloaded(Image image, String key, boolean returnedImmediately);
	}

	// @see com.biglybt.core.util.AEDiagnosticsEvidenceGenerator#generate(com.biglybt.core.util.IndentWriter)
	@Override
	public void generate(IndentWriter writer) {

		writer.println("ImageLoader for " + skinProperties);
		writer.indent();
		long[] sizeCouldBeFree = {
			0
		};
		long[] totalSizeEstimate = {
			0
		};
		try {
			writer.indent();
			try {
				writer.println("Non-Disposable:");
				writer.indent();
				for (String key : _mapImages.keySet()) {
					ImageLoaderRefInfo info = _mapImages.get(key);
					if (!info.isNonDisposable()) {
						continue;
					}
					writeEvidenceLine(writer, key, info, totalSizeEstimate,
							sizeCouldBeFree);
				}
				writer.exdent();
				writer.println("Disposable:");
				writer.indent();
				for (String key : _mapImages.keySet()) {
					ImageLoaderRefInfo info = _mapImages.get(key);
					if (info.isNonDisposable()) {
						continue;
					}
					writeEvidenceLine(writer, key, info, totalSizeEstimate,
							sizeCouldBeFree);
				}
				writer.exdent();
			} finally {
				writer.exdent();
			}
			if (totalSizeEstimate[0] > 0) {
				writer.println((totalSizeEstimate[0] / 1024)
						+ "k estimated used for images");
			}
			if (sizeCouldBeFree[0] > 0) {
				writer.println((sizeCouldBeFree[0] / 1024) + "k could be freed");
			}
		} finally {
			writer.exdent();
		}
	}

	/**
	 * @param writer
	 * @param info
	 */
	private void writeEvidenceLine(IndentWriter writer, String key,
			ImageLoaderRefInfo info, long[] totalSizeEstimate, long[] sizeCouldBeFree) {
		String line = info.getRefCount() + "] " + key;
		if (Utils.isThisThreadSWT()) {
			long sizeEstimate = 0;
			Image[] images = info.getImages();
			for (int i = 0; i < images.length; i++) {
				Image img = images[i];
				if (img != null) {
					if (img.isDisposed()) {
						line += "; *DISPOSED*";
					} else {
						Rectangle bounds = img.getBounds();
						long est = bounds.width * bounds.height * 4l;
						sizeEstimate += est;
						totalSizeEstimate[0] += est;
						if (info.canDispose()) {
							sizeCouldBeFree[0] += est;
						}
					}
				}
			}
			line += "; est " + sizeEstimate + " bytes";
		}
		writer.println(line);
	}

	public void addSkinProperties(SkinProperties skinProperties) {
		if (skinProperties == null) {
			return;
		}
		this.skinProperties.add(skinProperties);
		disabledOpacity = skinProperties.getIntValue(
				"imageloader.disabled-opacity", -1);
		notFound.clear();
	}

	/**
	 *
	 *
	 * @since 4.0.0.5
	 */
	public boolean collectGarbage() {
		if (Utils.isDisplayDisposed()) {
			return false;
		}
		return Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				int numRemoved = 0;
				for (Iterator<String> iter = _mapImages.keySet().iterator(); iter.hasNext();) {
					String key = iter.next();
					ImageLoaderRefInfo info = _mapImages.get(key);

					// no one can addref in between canDispose and dispose because
					// all our addrefs are in SWT threads.
					if (info != null && info.canDispose()) {
						if (DEBUG_UNLOAD) {
							System.out.println("dispose " + key);
						}
						iter.remove();
						numRemoved++;

						Image[] images = info.getImages();
						if ( images != null ){
							for (int j = 0; j < images.length; j++) {
								Image image = images[j];
								if (isRealImage(image)) {
									image.dispose();
								}
							}
						}
					}
				}
				//System.out.println("ImageLoader: GC'd " + numRemoved);
			}
		});
	}

	/**
	 * @param label
	 * @param key
	 *
	 * @since 4.0.0.5
	 */
	public void setLabelImage(Label label, String key) {
		if (key == null || label == null || label.isDisposed()) {
			return;
		}
		Image bg = getImage(key);
		if (!isRealImage(bg)) {
			return;
		}
		label.setImage(bg);
		label.addDisposeListener(e -> releaseImage(key));
	}

	public Image setButtonImage(Button btn, final String key) {
		Image bg = getImage(key);
		btn.setImage(bg);
		btn.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				releaseImage(key);
			}
		});

		return( bg );
	}

	public Image setToolItemImage(ToolItem btn, final String key) {
		Image bg = getImage(key);
		btn.setImage(bg);
		btn.addDisposeListener(e -> releaseImage(key));

		return( bg );
	}

	public void setBackgroundImage(Control control, final String key) {
		Image bg = getImage(key);
		control.setBackgroundImage(bg);
		control.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				releaseImage(key);
			}
		});
	}

	public SkinProperties[] getSkinProperties() {
		return skinProperties.toArray(new SkinProperties[0]);
	}

	public String findImageID(Image imageToFind) {
		String id = null;
		for (String key : _mapImages.keySet()) {
			Image[] images = _mapImages.get(key).getImages();
			if (images == null) {
				continue;
			}
			for (Image image : images) {
				if (image == imageToFind) {
					if (id == null) {
						id = key;
					} else {
						id += ", " + key;
					}
				}
			}
		}
		return id;
	}
	
	public static Object[] findWidgetWithDisposedImage(Composite c) {
		if (c == null) {
			return null;
		}
		Image img = c.getBackgroundImage();
		if (img != null && img.isDisposed()) {
			return new Object[] { c, img };
		}
		Control[] children = c.getChildren();
		for (Control child : children) {
			if (child instanceof Composite) {
				Object[] findMore = findWidgetWithDisposedImage((Composite) child);
				if (findMore != null) {
					return findMore;
				}
			}
			img = child.getBackgroundImage();
			if (img != null && img.isDisposed()) {
				return new Object[] { child, img };
			}
			if (child instanceof Button) {
				img = ((Button) child).getImage();
				if (img != null && img.isDisposed()) {
					return new Object[] { child, img };
				}
			} else if (child instanceof Label) {
				img = ((Label) child).getImage();
				if (img != null && img.isDisposed()) {
					return new Object[] { child, img };
				}
			}
			// TODO: Other types
			
		}
		return null;
	}


	public static String getBadDisposalDetails(Throwable e, Composite startAt) {
		try {
			if ((e instanceof SWTException)
				&& e.getMessage().equals("Graphic is disposed")) {
				if (startAt == null) {
					startAt = Utils.findAnyShell();
				}
				Object[] badBoys = ImageLoader.findWidgetWithDisposedImage(startAt);
				if (badBoys != null) {
					// breakpoint here to see if badBoys[1] has any getData to id the widget
					String imageID = ImageLoader.getInstance().findImageID((Image) badBoys[1]);
					return "Disposed Graphic id is "
						+ (imageID == null ? "(not found)" : imageID)
							+ ", parent is " + badBoys[0] + "; firstData="
							+ ((Widget) badBoys[0]).getData();
				}
			}
		} catch (Throwable ignore) {
		}
		return null;
	}
}
