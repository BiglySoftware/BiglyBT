package com.biglybt.ui.swt.config;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.Debug;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.shells.GCStringPrinter;


public class IconParameter extends Parameter implements ParameterListener {


  private Button iconChooser;
  private Image img;
  private String imgResource;

  private String file;
  
	public IconParameter(Composite composite, String configID, String _file,
			boolean hasDefault) {
		super(configID);
    iconChooser = new Button(composite,SWT.PUSH);
		if (configID == null) {
    	file = _file;
    } else {
			file = COConfigurationManager.getStringParameter(configID, _file);

			COConfigurationManager.addParameterListener(configID, this);
    }
    updateButtonIcon(composite.getDisplay(),file );

    if ( hasDefault ){
	    Menu menu = new Menu( iconChooser );
	    
	    iconChooser.setMenu( menu );
	    
	    MenuItem mi = new MenuItem( menu, SWT.PUSH );
	    
	    mi.setText( MessageText.getString( "menu.reset.icon" ));
	    
	    mi.addSelectionListener(
	    	new SelectionAdapter(){
	    		@Override
	    		public void widgetSelected(SelectionEvent e){
	    			 newIconChosen( null );
	    			 updateButtonIcon(iconChooser.getDisplay(), null);
	    		}	
			});
    }
    
    iconChooser.addListener(SWT.Dispose, new Listener() {
      @Override
      public void handleEvent(Event e) {
				if (configID != null) {
					COConfigurationManager.removeParameterListener(configID,
							IconParameter.this);
				}
      	releaseImage();
      }
    });

    iconChooser.addListener(SWT.Selection, new Listener() {
      @Override
      public void handleEvent(Event e) {
    	  FileDialog dialog = new FileDialog(iconChooser.getShell(), SWT.APPLICATION_MODAL);
          dialog.setFilterPath( file);
         
          String newFile = dialog.open();

          if ( newFile == null ){
        	  return;
          }
          
          newIconChosen(newFile);
				if (configID != null) {
					COConfigurationManager.setParameter(configID, newFile);
          } else {
        	  file = newFile;

        	  updateButtonIcon(iconChooser.getDisplay(), file);
          }
          newIconSet(newFile);
      }
    });

  }

  private void
  releaseImage()
  {
      if(imgResource != null ){
      	ImageLoader.getInstance().releaseImage( imgResource );
      	imgResource = null;
      }
      if ( img != null && !img.isDisposed()){
      	img.dispose();
      	img = null;
      }
  }
  
  private void updateButtonIcon(final Display display, String file) {
	  releaseImage();
	  if ( file == null ){
		  img = new Image(display,16,10);
		  GC gc = new GC(img);

		  Color color = iconChooser.getBackground();
		  gc.setBackground(color);
		  gc.fillRectangle(0,0,16,10);
		  new GCStringPrinter( gc, "-", new Rectangle( 0, 0, 16, 10 ), 0, SWT.CENTER ).printString();


		  gc.dispose();
		  iconChooser.setImage(img);
		 
	  }else{
		  try{
			  String resource = new File( file ).toURI().toURL().toExternalForm();
			  
			  ImageLoader.getInstance().getUrlImage(
					  resource, 
					  new Point( 16, 10 ),
					  new ImageLoader.ImageDownloaderListener(){
	
						  @Override
						  public void imageDownloaded(Image image, String key, boolean returnedImmediately){
							  							  
							  iconChooser.setImage(image);
							  
							  if ( image != null ){
								  imgResource = key;
							  }
						  }
					  });
	
		  }catch( Throwable e ){
	
			  Debug.out( e );
		  }
	  }
				
  }

  @Override
  public Control getControl() {
    return iconChooser;
  }

	@Override
	public void setLayoutData(Object layoutData) {
		iconChooser.setLayoutData(layoutData);
	}

  @Override
  public void parameterChanged(String parameterName) {
    file = COConfigurationManager.getStringParameter(configID);
  
    updateButtonIcon(iconChooser.getDisplay(), file);
  }

  public void newIconChosen(String file) {
    // subclasses can write their own code
  }

  public void newIconSet(String file) {
	  // subclasses can write their own code
  }

  @Override
  public void setValue(Object value) {
  	// not needed, we already trap external changes
  }

  public void setIcon(String _file) {
		file = _file;

		if (configID == null) {
			updateButtonIcon(iconChooser.getDisplay(), file);
		} else {
			COConfigurationManager.setParameter(configID, file);
		}
  }
}
