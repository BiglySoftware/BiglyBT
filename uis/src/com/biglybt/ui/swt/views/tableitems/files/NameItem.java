/*
 * File    : NameItem.java
 * Created : 24 nov. 2003
 * By      : Olivier
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.biglybt.ui.swt.views.tableitems.files;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrentFile;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Constants;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.FileUtil;
import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.swt.ImageRepository;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.debug.ObfuscateCellText;
import com.biglybt.ui.swt.debug.UIDebugGenerator;
import com.biglybt.ui.swt.imageloader.ImageLoader;
import com.biglybt.ui.swt.shells.GCStringPrinter;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.FilesView;
import com.biglybt.ui.swt.views.FilesViewMenuUtil;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;
import com.biglybt.ui.swt.views.table.TableCellSWT;
import com.biglybt.ui.swt.views.table.TableCellSWTPaintListener;
import com.biglybt.ui.swt.views.utils.ManagerUtils;
import com.biglybt.pif.ui.menus.MenuItem;
import com.biglybt.pif.ui.menus.MenuItemFillListener;
import com.biglybt.pif.ui.menus.MenuItemListener;
import com.biglybt.pif.ui.tables.*;

import com.biglybt.core.CoreOperation;
import com.biglybt.core.CoreOperationTask;

/** Torrent name cell for My Torrents.
 *
 * @author Olivier
 * @author TuxPaper (2004/Apr/17: modified to TableCellAdapter)
 */
public class NameItem extends CoreTableColumnSWT implements
	TableCellLightRefreshListener, ObfuscateCellText,
	TableCellSWTPaintListener, TableCellMouseMoveListener,
	TableCellInplaceEditorListener
{
	private static final String ID_CHECKHITAREA		= "checkHitArea";

	private static final Object	KEY_OLD_CELL_TT		= new Object();
	private static final Object	KEY_PATH_ICON		= new Object();
	
	private static boolean NEVER_SHOW_TWISTY;
	
	static{
		COConfigurationManager.addAndFireParameterListener(
			"FilesView.use.tree",
			new ParameterListener(){
				
				@Override
				public void parameterChanged(String parameterName){
					NEVER_SHOW_TWISTY =	!COConfigurationManager.getBooleanParameter("FilesView.use.tree");
				}
			});;	
	}

	
	private static boolean bShowIcon;

	private ParameterListener configShowProgramIconListener;

	final TableContextMenuItem menuItem;

	/** Default Constructor */
	public NameItem() {
		super("name", ALIGN_LEAD, POSITION_LAST, 300,
				TableManager.TABLE_TORRENT_FILES);
		setObfuscation(true);
		setInplaceEditorListener(this);
		setType(TableColumn.TYPE_TEXT);

		configShowProgramIconListener = new ParameterListener() {
			@Override
			public void parameterChanged(String parameterName) {
				bShowIcon = COConfigurationManager.getBooleanParameter("NameColumn.showProgramIcon");
				invalidateCells();
			}
		};
		COConfigurationManager.addWeakParameterListener(configShowProgramIconListener, true,
				"NameColumn.showProgramIcon");

		TableContextMenuItem menuShowIcon = addContextMenuItem(
				"ConfigView.section.style.showProgramIcon", MENU_STYLE_HEADER);
		menuShowIcon.setStyle(TableContextMenuItem.STYLE_CHECK);
		menuShowIcon.addFillListener(new MenuItemFillListener() {
			@Override
			public void menuWillBeShown(MenuItem menu, Object data) {
				menu.setData(Boolean.valueOf(bShowIcon));
			}
		});
		
		menuShowIcon.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				COConfigurationManager.setParameter("NameColumn.showProgramIcon",
						((Boolean) menu.getData()).booleanValue());
			}
		});
		
		menuItem = addContextMenuItem("Files.column.name.fastRename", MENU_STYLE_HEADER);

		menuItem.setStyle(MenuItem.STYLE_CHECK);
		menuItem.setData(Boolean.valueOf(hasInplaceEditorListener()));

		menuItem.addMultiListener(new MenuItemListener() {
			@Override
			public void selected(MenuItem menu, Object target) {
				menu.setData(Boolean.valueOf(!hasInplaceEditorListener()));
				setInplaceEditorListener(hasInplaceEditorListener() ? null : NameItem.this);
			}
		});
	}

	@Override
	public void remove() {
		super.remove();
		COConfigurationManager.removeWeakParameterListener(configShowProgramIconListener,
				"NameColumn.showProgramIcon");
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void postConfigLoad() {
		setInplaceEditorListener(getUserData("noInplaceEdit") == null ? null : this);
		menuItem.setData(Boolean.valueOf(hasInplaceEditorListener()));
	}

	@Override
	public void preConfigSave() {
		if(hasInplaceEditorListener())
			removeUserData("noInplaceEdit");
		else
			setUserData("noInplaceEdit", new Integer(1));
	}

	@Override
	public void refresh(TableCell cell, boolean sortOnlyRefresh)
	{
		final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
		String name = (fileInfo == null) ? "" : fileInfo.getFile(true).getName();
		if (name == null)
			name = "";

		cell.setSortValue( name );
	}

	@Override
	public void refresh(TableCell cell)
	{
		refresh(cell, false);
	}

	@Override
	public void cellPaint(GC gc, final TableCellSWT cell) {
		
		final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();

		Rectangle cellBounds = cell.getBounds();

		int	originalBoundxsX = cellBounds.x;
		
		int textX = originalBoundxsX;

		TableRowCore rowCore = cell.getTableRowCore();
		
		boolean showIcon = bShowIcon;
		
		if (rowCore != null) {
			int depth;
			boolean is_leaf;
			int	is_skipped = -1;
			
			if ( fileInfo instanceof FilesView.FilesViewTreeNode ){
				FilesView.FilesViewTreeNode node = (FilesView.FilesViewTreeNode)fileInfo;
				depth = node.getDepth();
				is_leaf = node.isLeaf();
				if ( !is_leaf ){
					showIcon = false;
					
					is_skipped = node.getSkippedState();
				}
			}else{
				depth = 0;
				is_leaf = true;
			}
			
			int numSubItems = rowCore.getSubItemCount();
			
			final int EXPANDOPADLEFT = 3;
			
			int paddingX = EXPANDOPADLEFT;
			
			final int EXPANDOWIDTH = 7;

			paddingX += depth*EXPANDOWIDTH;
			
			boolean	show_twisty;

			if ( NEVER_SHOW_TWISTY ){

				show_twisty = false;

			}else if (numSubItems > 1 || !is_leaf ){

				show_twisty = true;
			}else{

				show_twisty = false;
			}
			
			if (!NEVER_SHOW_TWISTY) {
				cellBounds.x += paddingX;
				cellBounds.width -= paddingX;
			}
			
			final int CHECKPADLEFT = 2;
			
			if (show_twisty){
				
				int middleY = cellBounds.y + (cellBounds.height / 2) - 1;
				int startX = cellBounds.x + paddingX;
				int halfHeight = 2;
				Color bg = gc.getBackground();
				gc.setBackground(gc.getForeground());
				gc.setAntialias(SWT.ON);
				gc.setAdvanced(true);
				if (rowCore.isExpanded()) {
					gc.fillPolygon(new int[] {
						startX,
						middleY - halfHeight,
						startX + EXPANDOWIDTH,
						middleY - halfHeight,
						startX + (EXPANDOWIDTH / 2),
						middleY + (halfHeight * 2) + 1
					});
				} else {
					gc.fillPolygon(new int[] {
						startX,
						middleY - halfHeight,
						startX + EXPANDOWIDTH,
						middleY + halfHeight,
						startX,
						middleY + (halfHeight * 2) + 1
					});
				}
				gc.setBackground(bg);
				//Rectangle hitArea = new Rectangle(paddingX*2, middleY - halfHeight
				//		- cellBounds.y, width, (halfHeight * 4) + 1);
				// expando is quite small, make it easier to hit
				Rectangle hitArea = new Rectangle(startX - originalBoundxsX - EXPANDOPADLEFT, 0, EXPANDOWIDTH + EXPANDOPADLEFT + CHECKPADLEFT, cellBounds.height);
				rowCore.setData(TableRowCore.ID_EXPANDOHITAREA, hitArea);
				rowCore.setData(TableRowCore.ID_EXPANDOHITCOLUMN, getName());
			}
			
			if (!NEVER_SHOW_TWISTY) {
				cellBounds.x += paddingX  + EXPANDOWIDTH;
				cellBounds.width -= paddingX + EXPANDOWIDTH;
			}
			
			ImageLoader im = ImageLoader.getInstance();
			
			String check_key;
			boolean is_ro = false;
			
			TOTorrentFile tf = fileInfo.getTorrentFile();
			
			if ( tf != null && tf.isPadFile()){
				
				is_ro = true;
				
				check_key = "check_ro_no";
				
			}else{
				
				if ( is_leaf ){
					Boolean isSkipped = fileInfo.isSkipping();
					
					if ( isSkipped == null ){
						isSkipped = fileInfo.isSkipped();
					}
					
					if (isSkipped){
						
						int	st = fileInfo.getStorageType();
						
						if ( 	st == DiskManagerFileInfo.ST_COMPACT ||
								st == DiskManagerFileInfo.ST_REORDER_COMPACT ){
							
							check_key = "check_no";
						}else{
							check_key = "check_no_grey";
						}
					}else{
						if ( fileInfo.getLength() == fileInfo.getDownloaded()){
							check_key = "check_ro_yes";
							is_ro = true;
						}else{
							check_key = "check_yes";
						}
					}
				}else{
					if ( is_skipped == 0 ){
						check_key = "check_no";
					}else if ( is_skipped == 1 ){
						if ( fileInfo.getLength() == fileInfo.getDownloaded()){
							check_key = "check_ro_yes";
							is_ro = true;
						}else{
							check_key = "check_yes";
						}
					}else{
						check_key = "check_maybe";
					}
				}
			}
			
			Image check = im.getImage(check_key);
			
			Rectangle checkSize = check.getBounds();
			
			int checkYOffset = (cellBounds.height - checkSize.height )/2 +1;
			
			gc.drawImage(check, cellBounds.x+CHECKPADLEFT, cellBounds.y + checkYOffset);
			
			im.releaseImage(check_key );
			
			if ( !is_ro ){
				Rectangle hitArea = new Rectangle( cellBounds.x+2 - originalBoundxsX, checkYOffset, checkSize.width, checkSize.height);
				
				rowCore.setData(ID_CHECKHITAREA, hitArea);
			}
			
			cellBounds.x += checkSize.width+4;
			cellBounds.width -= checkSize.width+4;

		}

		if (!showIcon) {
			cellBounds.x += 2;
			cellBounds.width -= 4;
			cellPaintName(cell, gc, cellBounds, cellBounds.x, originalBoundxsX);
			return;
		}

		Image[] imgThumbnail = null;

		if ( fileInfo != null ){
			
			File file = fileInfo.getFile(true);
					
			Object piCache = cell.getData( KEY_PATH_ICON );
		
			if ( piCache != null ){
				
				Object[] temp = (Object[])piCache;
				
				if ( FileUtil.areFilePathsIdentical((File)temp[0],file )){
					
					imgThumbnail = (Image[])temp[1];
				}
			}
			
			if ( imgThumbnail == null ){
			
				imgThumbnail = new Image[]{ ImageRepository.getPathIcon(file.getPath(), cell.getHeight() > 32, false) };
		
				cell.setData( KEY_PATH_ICON, new Object[]{ file, imgThumbnail });
			}
		}

		if (imgThumbnail != null && ImageLoader.isRealImage(imgThumbnail[0])) {
			try {

				if (cellBounds.height > 30) {
					cellBounds.y += 1;
					cellBounds.height -= 3;
				}
				Rectangle imgBounds = imgThumbnail[0].getBounds();

				int dstWidth;
				int dstHeight;
				if (imgBounds.height > cellBounds.height) {
					dstHeight = cellBounds.height;
					dstWidth = imgBounds.width * cellBounds.height / imgBounds.height;
				} else if (imgBounds.width > cellBounds.width) {
					dstWidth = cellBounds.width - 4;
					dstHeight = imgBounds.height * cellBounds.width / imgBounds.width;
				} else {
					dstWidth = imgBounds.width;
					dstHeight = imgBounds.height;
				}

				if (cellBounds.height <= 18) {
					dstWidth = Math.min(dstWidth, cellBounds.height);
					dstHeight = Math.min(dstHeight, cellBounds.height);
					if (imgBounds.width > 16) {
						cellBounds.y++;
						dstHeight -= 2;
					}
				}

				try {
					gc.setAdvanced(true);
					gc.setInterpolation(SWT.HIGH);
				} catch (Exception e) {
				}
				int x = cellBounds.x;
				textX = x + dstWidth + 3;
				int minWidth = dstHeight * 7 / 4;
				int imgPad = 0;
				if (dstHeight > 25) {
					if (dstWidth < minWidth) {
						imgPad = ((minWidth - dstWidth + 1) / 2);
						x = cellBounds.x + imgPad;
						textX = cellBounds.x + minWidth + 3;
					}
				}
				if (cellBounds.width - dstWidth - (imgPad * 2) < 100 && dstHeight > 18) {
					dstWidth = Math.min(32, dstHeight);
					x = cellBounds.x + ((32 - dstWidth + 1) / 2);
					dstHeight = imgBounds.height * dstWidth / imgBounds.width;
					textX = cellBounds.x + dstWidth + 3;
				}
				int y = cellBounds.y + ((cellBounds.height - dstHeight + 1) / 2);
				if (dstWidth > 0 && dstHeight > 0 && !imgBounds.isEmpty()) {
					//Rectangle dst = new Rectangle(x, y, dstWidth, dstHeight);
					Rectangle lastClipping = gc.getClipping();
					try {
						Utils.setClipping(gc, cellBounds);

						boolean hack_adv = Constants.isWindows8OrHigher && gc.getAdvanced();

						if ( hack_adv ){
								// problem with icon transparency on win8
							gc.setAdvanced( false );
						}

						for (int i = 0; i < imgThumbnail.length; i++) {
							Image image = imgThumbnail[i];
							if (image == null || image.isDisposed()) {
								continue;
							}
							Rectangle srcBounds = image.getBounds();
							if (i == 0) {
								int w = dstWidth;
								int h = dstHeight;
								if (imgThumbnail.length > 1) {
									w = w * 9 / 10;
									h = h * 9 / 10;
								}
								gc.drawImage(image, srcBounds.x, srcBounds.y, srcBounds.width,
										srcBounds.height, x, y, w, h);
							} else {
								int w = dstWidth * 3 / 8;
								int h = dstHeight * 3 / 8;
								gc.drawImage(image, srcBounds.x, srcBounds.y, srcBounds.width,
										srcBounds.height, x + dstWidth - w, y + dstHeight - h, w, h);
							}
						}

						if ( hack_adv ){
							gc.setAdvanced( true );
						}
					} catch (Exception e) {
						Debug.out(e);
					} finally {
						Utils.setClipping(gc, lastClipping);
					}
				}

			} catch (Throwable t) {
				Debug.out(t);
			}
		}

		cellPaintName(cell, gc, cellBounds, textX, originalBoundxsX);
	}
	
	private void cellPaintName(TableCell cell, GC gc, Rectangle cellBounds,
			int textX, int originalBoundxsX ) {
		final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
		
		String name = (fileInfo == null) ? "" : fileInfo.getFile(true).getName();
		
		if ( name == null ){
			
			name = "";
		}

		GCStringPrinter sp = new GCStringPrinter(gc, name, new Rectangle(textX,
				cellBounds.y, cellBounds.x + cellBounds.width - textX,
				cellBounds.height), true, true, getTableID().endsWith( ".big" )?SWT.WRAP:SWT.NULL );
		
		boolean fit = sp.printString();

		Point p = sp.getCalculatedPreferredSize();
			
		int pref = ( textX - originalBoundxsX ) +  p.x + 10;
		
		TableColumn tableColumn = cell.getTableColumn();
		if (tableColumn != null && tableColumn.getPreferredWidth() < pref) {
			tableColumn.setPreferredWidth(pref);
		}
		
		String tooltip = fit?"":name;

		cell.setToolTip(tooltip.length()==0?null:tooltip);
	}
	
	@Override
	public String getClipboardText(TableCell cell) {
		final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
		
		String name = (fileInfo == null) ? "" : fileInfo.getFile(true).getName();
		
		if ( name == null ){
			
			name = "";
		}
		return( name );
	}
	
	@Override
	public String getObfuscatedText(TableCell cell) {
		return( UIDebugGenerator.obfuscateFileName((DiskManagerFileInfo) cell.getDataSource()));
	}

	@Override
	public void cellMouseTrigger(TableCellMouseEvent event) {
		if (event.eventType == TableCellMouseEvent.EVENT_MOUSEMOVE
				|| event.eventType == TableRowMouseEvent.EVENT_MOUSEDOWN) {
			TableCell cell = event.cell;
			
			TableRow row = cell.getTableRow();
			if (row == null) {
				return;
			}
			
			boolean inArea = false;
			Object data = row.getData(TableRowCore.ID_EXPANDOHITAREA);
			if (data instanceof Rectangle) {
				Rectangle hitArea = (Rectangle) data;
				boolean inExpando = hitArea.contains(event.x, event.y);

				if ( inExpando ){
					inArea = true;
				
					if (event.eventType == TableCellMouseEvent.EVENT_MOUSEDOWN && event.button == 1) {
			
						if (row instanceof TableRowCore) {
							TableRowCore rowCore = (TableRowCore) row;
							rowCore.setExpanded(!rowCore.isExpanded());
						}
					}
				}
			}
			
			data = row.getData(ID_CHECKHITAREA);
			if (data instanceof Rectangle) {
				Rectangle hitArea = (Rectangle) data;
				boolean inCheck = hitArea.contains(event.x, event.y);

				String our_tt = Messages.getLanguageText( "filesview.name.check.tt" );
				
				Object set_tt = null;
				
				if ( inCheck ){
					
					final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)cell.getDataSource();

					boolean realFile;
					
					if ( fileInfo instanceof FilesView.FilesViewTreeNode ){
						
						realFile = ((FilesView.FilesViewTreeNode)fileInfo).isLeaf();
						
					}else{
						
						realFile = true;
					}
					
					if ( realFile ){
						
						set_tt = our_tt;
					}
					
					inArea = true;
					if (event.eventType == TableCellMouseEvent.EVENT_MOUSEDOWN && event.button == 1 ) {
						if (row instanceof TableRowCore) {
							TableRowCore rowCore = (TableRowCore) row;
							if ( rowCore != null ){
							
								Utils.getOffOfSWTThread( AERunnable.create(()->{
									
									if ( fileInfo instanceof FilesView.FilesViewTreeNode ){
										
										FilesView.FilesViewTreeNode node = (FilesView.FilesViewTreeNode)fileInfo;
										
										if ( !node.isLeaf()){
									
											int old_skipped = node.getSkippedState();
											
											boolean new_skipped;
											
											if ( old_skipped == 0 ){
												new_skipped = false;
											}else if ( old_skipped == 1 ){
												new_skipped = true;
											}else{
												new_skipped = true;
											}
											
											// disconcerting if we don't force the checkbox state
											// to update quickly...
										
											Utils.execSWTThreadLater( 100, ()->{
												
												cell.invalidate();
												
												rowCore.redraw( true );
											});
										
											node.setSkipped( new_skipped );
											
											return;
										}
									}
									
									if ( ( event.keyboardState & SWT.SHIFT ) != 0 && !fileInfo.isSkipped()){
										
										FilesViewMenuUtil.changePriority(
												FilesViewMenuUtil.PRIORITY_DELETE,
												Collections.singletonList(fileInfo), false );
									}else{
									
											// disconcerting if we don't force the checkbox state
											// to update quickly...
										
										Utils.execSWTThreadLater( 100, ()->{
											
											cell.invalidate();
											
											rowCore.redraw();
										});
																																																			
										ManagerUtils.setFileSkipped( fileInfo, !fileInfo.isSkipped());
									}
								}));
							}
						}
					}
				}
				
				if ( cell instanceof TableCellCore ){
					
					TableCellCore cellCore = (TableCellCore)cell;
										
					Object existingTT = cellCore.getToolTip();
					
					if ( existingTT != set_tt ){
						
						if ( existingTT instanceof String && set_tt != null && set_tt.equals( existingTT )){
							
							// no change
							
						}else{
							
								// we want to store any existing TTs we get that aren't ours
							
							if ( existingTT != null && !existingTT.equals( our_tt )){
								
								cellCore.setData( KEY_OLD_CELL_TT, existingTT );
							}
							
							if ( set_tt == null ){
								
								set_tt = cellCore.getData(KEY_OLD_CELL_TT );
							}
							
							cellCore.setToolTip( set_tt );
						}
					}
				}
			}
			
			if (event.eventType == TableCellMouseEvent.EVENT_MOUSEMOVE) {
				((TableCellCore)cell).setCursorID(inArea ? SWT.CURSOR_HAND : SWT.CURSOR_ARROW);
			}
		}
	}
	  
	@Override
	public boolean inplaceValueSet(TableCell cell, String value, boolean finalEdit) {
		if (value.equalsIgnoreCase(cell.getText()) || "".equals(value) || "".equals(cell.getText()))
			return true;
		final DiskManagerFileInfo fileInfo = (DiskManagerFileInfo) cell.getDataSource();
		final File target;

		try
		{
			target = new File(fileInfo.getFile(true).getParentFile(), value).getCanonicalFile();
		} catch (IOException e)
		{
			return false;
		}

		if(!finalEdit)
			return !target.exists();


		if(target.exists())
			return false;


		// code stolen from FilesView
		final boolean[] result = { false };
		boolean paused = fileInfo.getDownloadManager().pause( true );
		FileUtil.runAsTask(new CoreOperationTask()
		{
			@Override
			public String 
			getName()
			{
				return fileInfo.getFile(true).getName();
			}
			
			@Override
			public DownloadManager
			getDownload()
			{
				return( fileInfo.getDownloadManager());
			}
			
			@Override
			public String[] 
			getAffectedFileSystems()
			{
				return( FileUtil.getFileStoreNames( fileInfo.getDownloadManager().getAbsoluteSaveLocation(), target ));
			}

			
			@Override
			public void run(CoreOperation operation) {
				result[0] = fileInfo.setLink( target, false );
			}

			@Override
			public ProgressCallback getProgressCallback() {
				return null;
			}
		});
		if(paused)
			fileInfo.getDownloadManager().resume();

		if (!result[0])
		{
			new MessageBoxShell(SWT.ICON_ERROR | SWT.OK,
					MessageText.getString("FilesView.rename.failed.title"),
					MessageText.getString("FilesView.rename.failed.text")).open(null);
		}

		return true;
	}

}
