package com.biglybt.ui.swt.config;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.utils.FontUtils;

/**
 * SWT Parameter representing an Icon.<br>
 * Button with the icon drawn on it.
 * <p/>
 * Will always use 2 horizontal spaces in GridLayout
 */
public class IconSwtParameter
	extends BaseSwtParameter<IconSwtParameter, String>
{

	final private Button iconChooser;

	private Image img;

	private String imgResource;

	public IconSwtParameter(Composite composite, String configID, String labelKey,
			SwtParameterValueProcessor<IconSwtParameter, String> valueProcessor) {
		super(configID);

		createStandardLabel(composite, labelKey);

		iconChooser = new Button(composite, SWT.PUSH);
		setMainControl(iconChooser);

		if (doGridData(composite)) {
			GridData gridData = new GridData();
			gridData.horizontalSpan = labelKey == null ? 2 : 1;
			iconChooser.setLayoutData(gridData);
		}

		if (valueProcessor != null) {
			setValueProcessor(valueProcessor);
		} else if (paramID != null) {
			setConfigValueProcessor(String.class);
		}

		iconChooser.addListener(SWT.Dispose, e -> releaseImage());

		iconChooser.addListener(SWT.Selection, e -> {
			FileDialog dialog = new FileDialog(iconChooser.getShell(),
					SWT.APPLICATION_MODAL);
			dialog.setFilterExtensions(new String[] { "*.jpg;*.jpeg;*.png;*.gif;*.tiff;*.ico;*.bmp", "*.*"});
			dialog.setFilterNames(new String[] { "Images (gif, jpg, png, tiff, ico, bmp", "All" });
						
			boolean filterSet = false;
			
			String file = getValue();
			if (file != null) {
				File f = new File(file);
				
				if ( f.exists()){
					dialog.setFilterPath(f.getParent());
					dialog.setFileName( f.getName());
					
					filterSet = true;
				}
			}

			if ( !filterSet ){
				String dir = COConfigurationManager.getStringParameter("previous.filter.dir.icon");
				if ( dir != null && new File(dir).isDirectory()){
					dialog.setFilterPath( dir );
				}
			}
			
			String newFile = dialog.open();

			if (newFile == null) {
				return;
			}
			
			File newFilter = new File(newFile).getParentFile();
			
			if ( newFilter != null && newFilter.isDirectory()){
				COConfigurationManager.setParameter("previous.filter.dir.icon", newFilter.getAbsolutePath());
			}

			setValue(newFile);
		});

	    Menu menu = new Menu( iconChooser );
		    
	    iconChooser.setMenu( menu );
		    
	    MenuItem mi = new MenuItem( menu, SWT.PUSH );
		    
	    mi.setText( MessageText.getString( "menu.reset.icon" ));
		
	    mi.addListener(
	    	SWT.Selection,
	    	(e)->{ resetToDefault();});
	    
	    try{
	    	menu.addMenuListener( MenuListener.menuShownAdapter(
	    			(e)->{ mi.setEnabled( !isDefaultValue());}));
	    
		}catch( Throwable e ){
			
				// last win32 SWT 4757
			
			menu.addMenuListener(
				new MenuAdapter(){
					@Override
					public void menuShown(MenuEvent e){
						mi.setEnabled( !isDefaultValue());
					}
				});
		}
	}

	private void releaseImage() {
		if (imgResource != null) {
			ImageLoader.getInstance().releaseImage(imgResource);
			imgResource = null;
		}
		if (img != null && !img.isDisposed()) {
			if (!iconChooser.isDisposed()) {
				iconChooser.setImage(null);
				Utils.setTT( iconChooser, "" );
			}
			img.dispose();
			img = null;
		}
	}

	private void updateButtonIcon(String file) {
		releaseImage();
		int h = FontUtils.getFontHeightInPX(iconChooser.getFont());
		int w = (int) (h * 1.5);

		if (file == null) {

			img = new Image(iconChooser.getDisplay(), w, h);
			GC gc = new GC(img);

			Color color = iconChooser.getBackground();
			gc.setBackground(color);
			gc.fillRectangle(0, 0, w, h);
			new GCStringPrinter(gc, "-", new Rectangle(0, 0, w, h), 0,
					SWT.CENTER).printString();

			gc.dispose();
			iconChooser.setImage(img);
			Utils.setTT( iconChooser, "" );

		} else {
			try {
				File f = new File(file);
				
				ImageLoader.getInstance().getFileImage( f, new Point(w, h),
						(image, key, returnedImmediately) -> {

							iconChooser.setImage(image);
							
							if (image != null) {
								imgResource = key;
								Utils.setTT( iconChooser, f.getAbsolutePath());
							}else{
								Utils.setTT( iconChooser, "" );
							}
							
							if ( !returnedImmediately ){
							
								Utils.relayout(iconChooser);
							}
						});

			} catch (Throwable e) {

				Debug.out(e);
			}
		}
		Utils.relayout(iconChooser);
	}

	@Override
	public void refreshControl() {
		super.refreshControl();
		Utils.execSWTThread(() -> {
			updateButtonIcon(getValue());
		});
	}
}
