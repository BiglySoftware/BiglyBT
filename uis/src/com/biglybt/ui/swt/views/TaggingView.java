/*
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

package com.biglybt.ui.swt.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.TextViewerWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.pif.UISWTView;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewCoreEventListenerEx;
import com.biglybt.ui.swt.views.utils.TagButtonsUI;
import com.biglybt.ui.swt.views.utils.TagUIUtils;
import com.biglybt.ui.swt.widgets.TagCanvas.TagButtonTrigger;

import com.biglybt.core.tag.*;
import com.biglybt.core.tag.TagFeatureProperties.TagProperty;
import com.biglybt.ui.UIFunctions.TagReturner;

/**
 * View showing tags set on selected taggable item(s).  Sometimes easier than
 * drag and dropping to buttons/sidebar
 *
 * @author TuxPaper
 * @created Mar 23, 2015
 *
 */
public class TaggingView
	implements UISWTViewCoreEventListenerEx, TagTypeListener
{
	public static final String MSGID_PREFIX = "TaggingView";

	private UISWTView swtView;

	private Composite cMainComposite;

	private ScrolledComposite sc;

	private List<Taggable> taggables;

	private Composite parent;

	private TagButtonsUI 	tagButtonsUI;
	private Button			buttonExplain;
	
	public TaggingView() {
	}
	
	@Override
	public boolean
	isCloneable()
	{
		return( true );
	}

	@Override
	public UISWTViewCoreEventListenerEx
	getClone()
	{
		return( new TaggingView());
	}

	@Override
	public CloneConstructor
	getCloneConstructor()
	{
		return( 
			new CloneConstructor()
			{
				public Class<? extends UISWTViewCoreEventListenerEx>
				getCloneClass()
				{
					return( TaggingView.class );
				}
				
				public List<Object>
				getParameters()
				{
					return( null );
				}
			});
	}
	

	// @see com.biglybt.ui.swt.pif.UISWTViewEventListener#eventOccurred(com.biglybt.ui.swt.pif.UISWTViewEvent)
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
		switch (event.getType()) {
			case UISWTViewEvent.TYPE_CREATE:
				swtView = (UISWTView) event.getData();
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DESTROY:
				delete();
				break;

			case UISWTViewEvent.TYPE_INITIALIZE:
				parent = (Composite) event.getData();
				break;

			case UISWTViewEvent.TYPE_LANGUAGEUPDATE:
				Messages.updateLanguageForControl(cMainComposite);
				swtView.setTitle(getFullTitle());
				break;

			case UISWTViewEvent.TYPE_DATASOURCE_CHANGED:
				Object ds = event.getData();
				dataSourceChanged(ds);
				break;

			case UISWTViewEvent.TYPE_FOCUSGAINED:
				initialize();
				if (taggables == null) {
					dataSourceChanged(swtView.getDataSource());
				}
				break;

			case UISWTViewEvent.TYPE_FOCUSLOST:
				delete();
				break;

			case UISWTViewEvent.TYPE_REFRESH:
				refresh();
				break;
		}

		return true;
	}

	private void delete() {
		Utils.disposeComposite(sc);
		dataSourceChanged(null);
	}

	private void refresh() {
	}

	private void dataSourceChanged(Object ds) {
		boolean wasNull = taggables == null;

		if (ds instanceof Taggable) {
			taggables = new ArrayList<>();
			taggables.add((Taggable) ds);
		} else if (ds instanceof Taggable[]) {
			taggables = new ArrayList<>();
			taggables.addAll(Arrays.asList((Taggable[]) ds));
		} else if (ds instanceof Object[]) {
			taggables = new ArrayList<>();
			Object[] objects = (Object[]) ds;
			for (Object o : objects) {
				if (o instanceof Taggable) {
					Taggable taggable = (Taggable) o;
					if ( !taggables.contains( taggable )){
						taggables.add(taggable);
					}
				} else if (o instanceof DiskManagerFileInfo) {
					DownloadManager temp = ((DiskManagerFileInfo) o).getDownloadManager();
					if (temp != null) {
						if (!taggables.contains(temp)) {
							taggables.add(temp);
						}
					}
				}
			}
			if (taggables.size() == 0) {
				taggables = null;
			}
		} else {
			taggables = null;
		}

		boolean isNull = taggables == null;
		if (isNull != wasNull) {
			TagManager tm = TagManagerFactory.getTagManager();
			TagType tagType;
			/*
			tagType = tm.getTagType(TagType.TT_DOWNLOAD_CATEGORY);
			if (isNull) {
				tagType.removeTagTypeListener(this);
			} else {
				tagType.addTagTypeListener(this, false);
			}
			*/
			tagType = tm.getTagType(TagType.TT_DOWNLOAD_MANUAL);
			if (isNull) {
				tagType.removeTagTypeListener(this);
			} else {
				tagType.addTagTypeListener(this, false);
			}
		}

		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_updateFields();
			}
		});
	}

	private void initialize() {
		if (cMainComposite == null || cMainComposite.isDisposed()) {
			if (parent == null || parent.isDisposed()) {
				return;
			}
			sc = new ScrolledComposite(parent, SWT.V_SCROLL);
			sc.setExpandHorizontal(true);
			sc.setExpandVertical(true);
			sc.getVerticalBar().setIncrement(16);
			Layout parentLayout = parent.getLayout();
			if (parentLayout instanceof GridLayout) {
				GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
				sc.setLayoutData(gd);
			} else if (parentLayout instanceof FormLayout) {
				sc.setLayoutData(Utils.getFilledFormData());
			}

			cMainComposite = new Composite(sc, SWT.NONE);

			sc.setContent(cMainComposite);
		} else {
			Utils.disposeComposite(cMainComposite, false);
		}

		cMainComposite.setLayout(new GridLayout(1, false));

		tagButtonsUI = new TagButtonsUI();

		List<Tag> listAllTags = getTags();

		tagButtonsUI.buildTagGroup(listAllTags, cMainComposite,
				true, new TagButtonTrigger() {
					@Override
					public void tagButtonTriggered(Tag tag, boolean doTag) {
						for (Taggable taggable : taggables) {
							if (doTag) {
								tag.addTaggable(taggable);
							} else {
								tag.removeTaggable(taggable);
							}
							swt_updateFields();
						}
					}

					@Override
					public Boolean tagSelectedOverride(Tag tag) {
						return null;
					}
				});

		Composite buttonComp = new Composite( cMainComposite, SWT.NULL );
		
		buttonComp.setLayoutData( new GridData( GridData.FILL_HORIZONTAL ));
		
		buttonComp.setLayout(new GridLayout(2, false));
		
		Button buttonAdd = new Button(buttonComp, SWT.PUSH);
		buttonAdd.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, true, false));
		Messages.setLanguageText(buttonAdd, "label.add.tag");
		buttonAdd.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				TagUIUtils.createManualTag(new TagReturner() {
					@Override
					public void returnedTags(Tag[] tags) {
						if (taggables == null) {
							return;
						}
						for (Tag tag : tags) {
							for (Taggable taggable : taggables) {
								tag.addTaggable(taggable);
							}
						}
					}
				});
			}
		});

		buttonExplain = new Button(buttonComp, SWT.PUSH);
		buttonExplain.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, false, false));
		Messages.setLanguageText(buttonExplain, "button.explain");
		buttonExplain.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				explain();
			}});
				
		sc.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle r = sc.getClientArea();
				Point size = cMainComposite.computeSize(r.width, SWT.DEFAULT);
				sc.setMinSize(size);
			}
		});

		swt_updateFields();

		Rectangle r = sc.getClientArea();
		Point size = cMainComposite.computeSize(r.width, SWT.DEFAULT);
		sc.setMinSize(size);
	}

	private String getFullTitle() {
		return MessageText.getString("label.tags");
	}

	private void swt_updateFields() {

		if (cMainComposite == null || cMainComposite.isDisposed()) {
			return;
		}

		if (tagButtonsUI.updateFields(taggables)) {
			parent.layout();
		}
		
		boolean has_constraint = false;

		if ( taggables != null ){
						
			List<Tag> listAllTags = getTags();
					
			for ( Tag tag: listAllTags ){
				
				TagFeatureProperties	tfp = (TagFeatureProperties)tag;
				
				String[] constraint = tfp.getProperty( TagFeatureProperties.PR_CONSTRAINT ).getStringList();
				
				if ( constraint != null && constraint.length > 0 ){
					
					String s = constraint[0];
					
					if ( s.length() > 0 ){
						
						has_constraint = true;
						
						break;
					}
				}
			}
		}
		
		buttonExplain.setEnabled( has_constraint );
	}

	private List<Tag>
	getTags()
	{
		TagManager tm = TagManagerFactory.getTagManager();
		
		int[] tagTypesWanted = {
			TagType.TT_DOWNLOAD_MANUAL,
			//TagType.TT_DOWNLOAD_CATEGORY
		};
		
		List<Tag> listAllTags = new ArrayList<>();

		for (int tagType : tagTypesWanted) {

			TagType tt = tm.getTagType(tagType);
			List<Tag> tags = tt.getTags();
			listAllTags.addAll(tags);
		}
		
		return( listAllTags );
	}
	
	@Override
	public void tagTypeChanged(TagType tag_type) {
	}

	@Override
	public void tagEventOccurred(TagEvent event ) {
		int	type = event.getEventType();
		Tag	tag = event.getTag();
		if ( type == TagEvent.ET_TAG_ADDED ){
			tagAdded( tag );
		}else if ( type == TagEvent.ET_TAG_MEMBERSHIP_CHANGED || type == TagEvent.ET_TAG_METADATA_CHANGED ){
			tagChanged( tag );
		}else if ( type == TagEvent.ET_TAG_REMOVED ){
			tagRemoved( tag );
		}
	}

	public void tagAdded(Tag tag) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				initialize();
			}
		});
	}

	public void tagChanged(final Tag changedTag) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				swt_updateFields();
			}
		});
	}

	public void tagRemoved(Tag tag) {
		Utils.execSWTThread(new AERunnable() {
			@Override
			public void runSupport() {
				initialize();
			}
		});
	}

	private void
	explain()
	{
		List<Tag> tags = getTags();
		
		TagUtils.sortTags( tags );
		
		StringBuilder content = new StringBuilder(1024);
		
		for ( Taggable t: taggables ){
			
			content.append( t.getTaggableName() + "\n\n" );
			
			for ( Tag tag: tags ){
				
				TagFeatureProperties	tfp = (TagFeatureProperties)tag;
				
				TagProperty tfp_constraint = tfp.getProperty( TagFeatureProperties.PR_CONSTRAINT );
				
				String[] constraint = tfp_constraint.getStringList();
				
				if ( constraint != null && constraint.length > 0 ){
					
					String s = constraint[0];
					
					if ( s.length() > 0 ){

						String details = tfp_constraint.explainTaggable( t );
						
						content.append( tag.getTagName(true) + " -> " + details + "\n" );
					}
				}
			}
			
			content.append( "\n" );
		}
		
		new TextViewerWindow(
				MessageText.getString( "label.details" ),
				null, content.toString(), false  );

	}
}
