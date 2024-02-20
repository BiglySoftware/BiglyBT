/*
 * File : SingleFilePanel.java Created : 30 sept. 2003 02:50:19 By : Olivier
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 */

package com.biglybt.ui.swt.maketorrent;

import java.io.File;
import java.util.*;
import java.util.List;

import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.pif.ui.UIInputReceiverListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.mainwindow.TorrentOpener;
import com.biglybt.ui.swt.wizard.AbstractWizardPanel;
import com.biglybt.ui.swt.wizard.IWizardPanel;

import com.biglybt.util.MapUtils;

/**
 * @author Olivier
 *
 */
public class BYOPanel
	extends AbstractWizardPanel<NewTorrentWizard>
{

	private Tree tree;

	public BYOPanel(NewTorrentWizard wizard,
			IWizardPanel<NewTorrentWizard> previous) {
		super(wizard, previous);

		//wizard.byo_map = null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.biglybt.ui.swt.maketorrent.IWizardPanel#show()
	 */
	@Override
	public void show() {
		wizard.setTitle(MessageText.getString("wizard.newtorrent.byo"));
		wizard.setCurrentInfo(MessageText.getString("wizard.newtorrent.byo.info"));
		Composite panel = wizard.getPanel();
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		panel.setLayout(layout);

		GridData gridData;

		tree = new Tree(panel, SWT.BORDER | SWT.MULTI);
		tree.setHeaderVisible(true);
		TreeColumn treeColumn = new TreeColumn(tree, 0);
		Messages.setLanguageText(treeColumn, "label.torrent.structure");
		treeColumn.setWidth(180);
		treeColumn = new TreeColumn(tree, 0);
		Messages.setLanguageText(treeColumn, "label.original.file");
		treeColumn.setWidth(500);
		gridData = new GridData(GridData.FILL_BOTH);
		tree.setLayoutData(gridData);

		createDropTarget(tree);
		createDragSource(tree);

		tree.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				editSelected();
				e.doit = false;
			}
		});

		tree.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (e.keyCode == SWT.F2) {
					editSelected();
				} else if (e.keyCode == SWT.DEL) {
					TreeItem[] selection = tree.getSelection();
					for (TreeItem treeItem : selection) {
						Object data = treeItem.getData();
						treeItem.dispose();
					}
				}
			}
		});

		Composite cButtons = new Composite(panel, 0);
		cButtons.setLayout(new RowLayout());
		cButtons.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Button btnAddContainer = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(btnAddContainer, "button.add.container");
		btnAddContainer.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
						"wizard.newtorrent.byo.addcontainer.title",
						"wizard.newtorrent.byo.addcontainer.text");
				entryWindow.setPreenteredText("files", true);
				entryWindow.prompt(new UIInputReceiverListener() {
					@Override
					public void UIInputReceiverClosed(UIInputReceiver entryWindow) {
						if (entryWindow.hasSubmittedInput()) {
							createContainer(null, entryWindow.getSubmittedInput());
						}
					}
				});
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnAddFiles = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(btnAddFiles, "OpenTorrentWindow.addFiles");
		btnAddFiles.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog fDialog = new FileDialog(Utils.findAnyShell(), SWT.OPEN | SWT.MULTI);
				fDialog.setFilterPath(TorrentOpener.getFilterPathData());
				fDialog.setText(MessageText.getString("MainWindow.dialog.choose.file"));
				if (fDialog.open() != null) {
					String[] fileNames = fDialog.getFileNames();
					File last_file = null;
					for (String fileName : fileNames) {
						File f = new File(fDialog.getFilterPath(), fileName);
						addFilename(f);
						last_file = f;
					}

					if ( last_file != null ){

						TorrentOpener.setFilterPathData( last_file.getAbsolutePath());
					}
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		Button btnAddFolder = new Button(cButtons, SWT.PUSH);
		Messages.setLanguageText(btnAddFolder, "OpenTorrentWindow.addFiles.Folder");
		btnAddFolder.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog fDialog = new DirectoryDialog(Utils.findAnyShell(), SWT.NULL);
				fDialog.setFilterPath(TorrentOpener.getFilterPathData());
				fDialog.setMessage(MessageText.getString("MainWindow.dialog.choose.folder"));
				String path = fDialog.open();
				if (path != null) {
					File f = new File(path);
					addFilename(f);

					if ( f.isDirectory()){
						TorrentOpener.setFilterPathData( f.getAbsolutePath());
					}
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

			// restore state if there is any

		if ( wizard.create_mode == wizard.MODE_DIRECTORY || wizard.create_mode == wizard.MODE_SINGLE_FILE ){
			
			String path = wizard.create_mode == wizard.MODE_DIRECTORY?wizard.directoryPath:wizard.singlePath;
			
			if ( path != null ){
				
				File file = new File( path );
				
				if ( file.exists()){
					addFilename( file );
				}
			}
		}else{
			if (wizard.byo_map != null) {
				List list = (List) wizard.byo_map.get("file_map");
				if (list != null) {
					for (Iterator iterator = list.iterator(); iterator.hasNext();) {
						Map map = (Map) iterator.next();
						String target = MapUtils.getMapString(map, "target", null);
						List path = MapUtils.getMapList(map, "logical_path", null);
						if (target != null && path != null) {
							File targetFile = new File(target);
							if (path.size() == 1) {
								addFilename(targetFile, (String) path.get(0), null, true);
							} else {
								TreeItem[] items = tree.getItems();
								TreeItem parent = null;
								for (int i = 0; i < path.size() - 1; i++) {
									TreeItem lastParent = parent;
									String name = (String) path.get(i);
	
									boolean found = false;
									for (TreeItem item : items) {
										if (item.getText().equals(name)) {
											parent = item;
											found = true;
											break;
										}
									}
									if (!found) {
										parent = createContainer(lastParent, name);
									}
									items = parent.getItems();
								}
								String name = (String) path.get(path.size() - 1);
								addFilename(targetFile, name, parent, false);
							}
						}
					}
				}
			}
		}
	}

	private void createDragSource(final Tree tree) {
		Transfer[] types = new Transfer[] { TextTransfer.getInstance() };
    int operations = DND.DROP_MOVE;

    final DragSource source = new DragSource(tree, operations);
    source.setTransfer(types);
    source.addDragListener(new DragSourceListener() {

			@Override
			public void dragStart(DragSourceEvent event) {
				TreeItem[] selection = tree.getSelection();
        event.doit = selection.length > 0;
				tree.setData("dragging", 1);
      }

	    @Override
      public void dragSetData(DragSourceEvent event) {
        event.data = "drag";
        event.detail = DND.DROP_MOVE;
      }

			@Override
			public void dragFinished(DragSourceEvent event) {
				tree.setData("dragging", null);
			}

    });

    tree.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				source.dispose();
			}
		});
	}

	protected void editSelected() {
		final TreeItem[] selection = tree.getSelection();
		if (selection.length == 1) {
			SimpleTextEntryWindow entryWindow = new SimpleTextEntryWindow(
					"wizard.newtorrent.byo.editname.title",
					"wizard.newtorrent.byo.editname.text");
			entryWindow.setPreenteredText(selection[0].getText(), false);
			entryWindow.prompt(new UIInputReceiverListener() {
				@Override
				public void UIInputReceiverClosed(UIInputReceiver receiver) {
					if (receiver.hasSubmittedInput()) {
						selection[0].setText(receiver.getSubmittedInput());
					}
				}
			});
		}
	}

	private void createDropTarget(final Tree tree) {
		final DropTarget dropTarget = new DropTarget(tree, DND.DROP_MOVE
				| DND.DROP_LINK);
		dropTarget.setTransfer(new Transfer[] {
			TextTransfer.getInstance(),
			FileTransfer.getInstance()
		});
		dropTarget.addDropListener(new DropTargetAdapter() {
			@Override
			public void dragOver(DropTargetEvent event) {
				event.detail = DND.DROP_DEFAULT;
				event.feedback = DND.FEEDBACK_EXPAND | DND.FEEDBACK_SCROLL;
				if (event.item instanceof TreeItem) {
					TreeItem item = (TreeItem) event.item;

					if (tree.getData("dragging") != null) {
						TreeItem[] selection = tree.getSelection();
						boolean ok = true;
						for (TreeItem treeItem : selection) {
							if (treeItem == item) {
								ok = false;
								break;
							}
							if (item.getData() == null) {
								// dragging to container
								if (treeItem.getParentItem() == item) {
									ok = false;
									break;
								}
							} else {
								if (treeItem.getParentItem() == item.getParentItem()) {
									ok = false;
									break;
								}
							}
						}
						if (!ok) {
							event.detail = DND.DROP_NONE;
							return;
						}
					}


					if (item.getData() == null) {
						event.feedback |= DND.FEEDBACK_SELECT;
					} else {
						event.feedback |= DND.FEEDBACK_INSERT_AFTER;
					}
				}
			}

			@Override
			public void drop(DropTargetEvent event) {
				if (event.data instanceof String[]) {
					String[] sourceNames = (String[]) event.data;
					if (sourceNames == null)
						event.detail = DND.DROP_NONE;
					if (event.detail == DND.DROP_NONE)
						return;

					for (String droppedFileStr : sourceNames) {
						File droppedFile = new File(droppedFileStr);
						addFilename(droppedFile, (TreeItem) event.item);
					}
				} else if ("drag".equals(event.data)) {
					TreeItem[] selection = tree.getSelection();
					for (TreeItem treeItem : selection) {
						if (!treeItem.isDisposed()) {
							moveItem(treeItem, (TreeItem) event.item);
						}
					}
				}
			}
		});

    tree.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				dropTarget.dispose();
			}
		});

	}

	protected void addFilename(File file) {
		addFilename(file, file.getName(), null, false);
	}

	protected void addFilename(File file, TreeItem parent) {
		addFilename(file, file.getName(),parent, false);
	}

	protected void addFilename(File file, String name, TreeItem parent, boolean init) {
		if (parent != null && parent.getData() != null) {
			parent = parent.getParentItem();
		}
		TreeItem firstItem = tree.getItemCount() > 0 ? tree.getItem(0) : null;
		if (firstItem != null && firstItem.getData() != null) {
			parent = createContainer(null, file.getParentFile().getName());
		} else if (parent == null) {
			parent = firstItem;
		}

		if (!file.exists()) {
			//dropping container from a drag
			createContainer(null, name);
		}

		TreeItem treeItem = parent == null ? new TreeItem(tree, 0)
				: new TreeItem(parent, 0);
		treeItem.setText(new String[] {
			name,
			file.getAbsolutePath()
		});
		treeItem.setData(file);

		if (parent != null) {
			parent.setExpanded(true);
		}

		wizard.setNextEnabled(tree.getItemCount() >= 1);
	}

	private TreeItem createContainer(TreeItem parent, String name) {
		TreeItem[] selection = tree.getSelection();
		//boolean moveSelected = false;
		if (parent == null) {
  		if (selection.length == 1 && selection[0].getData() == null) {
  			parent = selection[0];
  		} else if (selection.length > 0) {
  			parent = selection[0].getParentItem();
  			//moveSelected = true;
  		} else {
  			TreeItem firstItem = tree.getItemCount() > 0 ? tree.getItem(0) : null;
  			if (firstItem != null && firstItem.getData() == null) {
  				parent = firstItem;
  			}
  		}
		}

		TreeItem item = parent == null ? new TreeItem(tree, 0, 0) : new TreeItem(parent, 0, 0);
		item.setText(new String[] {
			name,
			MessageText.getString( "label.container.display")
		});

		while (tree.getItemCount() > 1) {
			TreeItem itemToMove = tree.getItem(1);
			moveItem(itemToMove, item);
		}
		item.setExpanded(true);

		/*
		if (moveSelected) {
			// move selected into new item
			for (TreeItem itemToMove : selection) {
				moveItem(itemToMove, item);
			}
		}
		*/

		return item;
	}

	private void moveItem(TreeItem itemToMove, TreeItem parent) {
		if (parent == null) {
			if (tree.getItemCount() == 0) {
				return;
			}
			parent = tree.getItem(0);
		}
		File parentFile = (File) parent.getData();
		if (parentFile != null && !parentFile.isDirectory()) {
			parent = parent.getParentItem();
		}
		TreeItem itemNew = new TreeItem(parent, 0);
		for (int i = 0; i < tree.getColumnCount(); i++) {
			itemNew.setText(i, itemToMove.getText(i));
		}
		File file = (File) itemToMove.getData();
		itemNew.setData(file);
		while (itemToMove.getItemCount() > 0) {
			TreeItem subitemToMove = itemToMove.getItem(0);
			moveItem(subitemToMove, itemNew);
		}
		itemToMove.dispose();
	}

	private void
	saveState()
	{
		if (tree.getItemCount() == 1) {
				// might be single file or single directory
			TreeItem item = tree.getItem(0);
			String name = item.getText();
			File file = (File) item.getData();
			if (file != null && file.getName().equals(name) && file.exists()) {
				String	parent = file.getParent();
				if ( parent != null ){
					((NewTorrentWizard) wizard).setDefaultOpenDir( parent );
				}

				if (file.isDirectory()) {
					wizard.directoryPath = file.getAbsolutePath();
					wizard.create_mode = wizard.MODE_DIRECTORY;

				} else {
					wizard.singlePath = file.getAbsolutePath();
					wizard.create_mode = wizard.MODE_SINGLE_FILE;
				}
				
				return;
			}
		}
		
		wizard.create_mode = wizard.MODE_BYO;
		
		if ( tree.getItemCount() == 0 ){
			
			wizard.byo_map = null;
			
		}else{
			
			Map map = new HashMap();
	
			List<Map> list = new ArrayList<>();
	
			map.put("file_map", list);
	
			buildList(list, tree.getItems());
	
			wizard.byo_map = map;
	
			try {
				wizard.byo_desc_file = AETemporaryFileHandler.createTempFile();
	
				FileUtil.writeBytesAsFile(wizard.byo_desc_file.getAbsolutePath(),
						BEncoder.encode(map));
	
			} catch (Throwable e) {
	
				Debug.out(e);
			}
		}
	}
	
	@Override
	public IWizardPanel<NewTorrentWizard> 
	getPreviousPanel() 
	{
		saveState();
		
		return( super.getPreviousPanel());
	}
	  
	@Override
	public IWizardPanel<NewTorrentWizard> 
	getNextPanel() 
	{
		saveState();
		
		return new SavePathPanel(wizard, this);
	}

	private void buildList(List list, TreeItem[] items) {
		for (TreeItem treeItem : items) {
			if (treeItem == null || treeItem.isDisposed()) {
				continue;
			}

			TreeItem[] subItems = treeItem.getItems();
			File file = (File) treeItem.getData();
			if (file != null) {
				Map m = new HashMap();

				list.add(m);

				List<String> path = new ArrayList<>();
				do {
					path.add(0, treeItem.getText());
					treeItem = treeItem.getParentItem();
				} while (treeItem != null);


				m.put("logical_path", path);
				m.put("target", file.getAbsolutePath());
			}
			if (subItems.length > 0) {
				buildList(list, subItems);
			}
		}
	}
}
