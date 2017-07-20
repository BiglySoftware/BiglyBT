/*
 * Created on 28-Apr-2004
 * Created by Paul Gardner
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

package com.biglybt.ui.swt.pifimpl;

/**
 * @author parg
 *
 */

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.pif.ui.config.*;
import com.biglybt.pif.ui.model.BasicPluginConfigModel;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.components.LinkLabel;
import com.biglybt.ui.swt.config.*;
import com.biglybt.ui.swt.config.BooleanParameter;
import com.biglybt.ui.swt.config.DirectoryParameter;
import com.biglybt.ui.swt.config.InfoParameter;
import com.biglybt.ui.swt.config.IntParameter;
import com.biglybt.ui.swt.config.Parameter;
import com.biglybt.ui.swt.config.PasswordParameter;
import com.biglybt.ui.swt.config.StringListParameter;
import com.biglybt.ui.swt.config.StringParameter;
import com.biglybt.ui.swt.mainwindow.ClipboardCopy;
import com.biglybt.ui.swt.pif.UISWTConfigSection;
import com.biglybt.ui.swt.pif.UISWTParameterContext;

public class
BasicPluginConfigImpl
	implements UISWTConfigSection
{
	protected WeakReference<BasicPluginConfigModel>		model_ref;

	protected String					parent_section;
	protected String					section;

	public
	BasicPluginConfigImpl(
		WeakReference<BasicPluginConfigModel>	_model_ref )
	{
		model_ref			= _model_ref;

		BasicPluginConfigModel	model = model_ref.get();

		parent_section	= model.getParentSection();
		section			= model.getSection();
	}

	@Override
	public String
	configSectionGetParentSection()
	{
		if ( parent_section == null || parent_section.length() == 0 ){

			return( ConfigSection.SECTION_ROOT );
		}

		return( parent_section );
	}

	@Override
	public String
	configSectionGetName()
	{
		return( section );
	}


	@Override
	public void
	configSectionSave()
	{

	}


	@Override
	public void
	configSectionDelete()
	{

	}

	@Override
	public int
	maxUserMode()
	{
		BasicPluginConfigModel	model = model_ref.get();

		int	max_mode = 0;

		if ( model != null ){

			com.biglybt.pif.ui.config.Parameter[] parameters = model.getParameters();

			for (int i=0;i<parameters.length;i++){

				ParameterImpl	param = 	(ParameterImpl)parameters[i];

				if ( param.getMinimumRequiredUserMode() > max_mode ){

					max_mode = param.getMinimumRequiredUserMode();
				}
			}
		}

		return( max_mode );
	}


	@Override
	public Composite
	configSectionCreate(
		final Composite parent )
	{
		int userMode = COConfigurationManager.getIntParameter("User Mode");

			// main tab set up

		Composite main_tab = new Composite(parent, SWT.NULL);

		GridData main_gridData = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);

		Utils.setLayoutData(main_tab, main_gridData);

		GridLayout layout = new GridLayout();

		layout.numColumns = 2;

		layout.marginHeight = 0;

		main_tab.setLayout(layout);

		final Map	comp_map	= new HashMap();

		Composite current_composite	= main_tab;

		Map<ParameterGroupImpl,Composite>		group_map 		= new HashMap<>();

		Map<ParameterTabFolderImpl,CTabFolder>	tab_folder_map	= new HashMap<>();
		Map<ParameterGroupImpl,Composite>		tab_map			= new HashMap<>();


		BasicPluginConfigModel	model = model_ref.get();

		if ( model == null ){

			return( main_tab );
		}

		com.biglybt.pif.ui.config.Parameter[] parameters = model.getParameters();

		List<Button>	buttons = new ArrayList<>();

		for (int i=0;i<parameters.length;i++){

			final ParameterImpl	param = 	(ParameterImpl)parameters[i];

			if ( param.getMinimumRequiredUserMode() > userMode ){

				continue;
			}

			ParameterGroupImpl	pg = param.getGroup();

			if ( pg == null ){

				current_composite = main_tab;

			}else{

				ParameterTabFolderImpl	tab_folder = pg.getTabFolder();

				if ( tab_folder != null ){

					ParameterGroupImpl tab_group = tab_folder.getGroup();

					CTabFolder	tf = tab_folder_map.get( tab_folder );

					if ( tf == null ){

						Composite tab_parent = current_composite;

						if ( tab_group != null ){

							String tg_resource = tab_group.getResourceName();

							if ( tg_resource != null ){

								tab_parent = group_map.get( tab_group );

								if ( tab_parent == null ){

									tab_parent = new Group( current_composite, SWT.NULL);

									Messages.setLanguageText(tab_parent, tg_resource );

									GridData gridData = new GridData(GridData.FILL_HORIZONTAL );

									gridData.horizontalSpan = 2;

									if ( tab_group.getMinimumRequiredUserMode() > userMode ){

										tab_parent.setVisible( false );

										gridData.widthHint = 0;
										gridData.heightHint = 0;
									}

									Utils.setLayoutData(tab_parent, gridData);

									layout = new GridLayout();

									layout.numColumns = tab_group.getNumberColumns() * 2;

									tab_parent.setLayout(layout);

									group_map.put( tab_group, tab_parent );
								}
							}
						}

						tf = new CTabFolder( tab_parent, SWT.LEFT );

						tf.setBorderVisible( tab_group == null );

						tf.setTabHeight(20);

						GridData grid_data = new GridData( GridData.FILL_HORIZONTAL );

						grid_data.horizontalSpan = 2;

						if ( tab_folder.getMinimumRequiredUserMode() > userMode ){

							tf.setVisible( false );

							grid_data.widthHint = 0;
							grid_data.heightHint = 0;
						}

						Utils.setLayoutData(tf, grid_data);

						tab_folder_map.put( tab_folder, tf );
					}

					Composite tab_composite = tab_map.get( pg );

					if ( tab_composite == null ){

						CTabItem tab_item = new CTabItem(tf, SWT.NULL);

						String tab_name = pg.getResourceName();

						if ( tab_name != null ){

							Messages.setLanguageText( tab_item, tab_name );
						}

						tab_composite = new Composite( tf, SWT.NONE );
						tab_item.setControl( tab_composite );

						layout = new GridLayout();
						layout.numColumns = 2;

						tab_composite.setLayout(layout);

						GridData grid_data = new GridData(GridData.FILL_BOTH);

						if ( pg.getMinimumRequiredUserMode() > userMode ){

							tab_composite.setVisible( false );

							grid_data.widthHint = 0;
							grid_data.heightHint = 0;
						}

						Utils.setLayoutData(tab_composite,  grid_data );

						if ( tf.getItemCount() == 1 ){

							tf.setSelection( tab_item );
						}

						tab_map.put( pg, tab_composite );
					}

					current_composite = tab_composite;
				}

				Composite comp = group_map.get( pg );

				if ( comp == null ){

					boolean nested = pg.getGroup() != null || tab_folder != null;

					Composite group_parent = nested?current_composite:main_tab;

					String resource_name = pg.getResourceName();

					boolean use_composite = resource_name == null || tab_folder != null;

					current_composite = use_composite?new Composite( group_parent, SWT.NONE ):new Group( group_parent, SWT.NULL);

					if ( !use_composite ){

						Messages.setLanguageText(current_composite, resource_name );
					}

					GridData grid_data = new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL);

					grid_data.grabExcessHorizontalSpace = true;
					grid_data.horizontalSpan = 2;

					if ( pg.getMinimumRequiredUserMode() > userMode ){

						current_composite.setVisible( false );

						grid_data.widthHint = 0;
						grid_data.heightHint = 0;
					}

					Utils.setLayoutData(current_composite,  grid_data );

					layout = new GridLayout();

					layout.numColumns = pg.getNumberColumns() * 2;

					current_composite.setLayout(layout);

					group_map.put( pg, current_composite );

				}else{

					current_composite = comp;
				}
			}

			Label label = null;

			String	label_key = param.getLabelKey();

			String	label_text = label_key==null?param.getLabelText():MessageText.getString( label_key );

				// we can only use the check-box's label form for boolean params if the text
				// doesn't include formatting (it doesn't handle it)

			if ( 	label_text.indexOf('\n') != -1 ||
					label_text.indexOf('\t') != -1 ||
					!(param instanceof BooleanParameterImpl)) {

				String hyperlink = null;
				if (param instanceof HyperlinkParameterImpl) {
					hyperlink = ((HyperlinkParameterImpl)param).getHyperlink();
				}

				label = new Label(current_composite, (param instanceof LabelParameterImpl) ? SWT.WRAP : SWT.NULL);

				boolean	add_copy;

				if ( label_key == null ){
					label.setText( param.getLabelText());
					add_copy = true;
				}else{
					Messages.setLanguageText(label, label_key );
					add_copy = label_key.startsWith( "!" );
				}

				if ( add_copy ){
					final Label f_label = label;

					ClipboardCopy.addCopyToClipMenu(
							label,
							new ClipboardCopy.copyToClipProvider()
							{
								@Override
								public String
								getText()
								{
									return( f_label.getText().trim());
								}
							});
				}

				if (hyperlink != null) {
					LinkLabel.makeLinkedLabel(label, hyperlink);
				}

				if (param instanceof HyperlinkParameterImpl) {

					final Label f_label = label;

					param.addListener(
							new ParameterListener()
							{
								@Override
								public void
								parameterChanged(
									com.biglybt.pif.ui.config.Parameter	p )
								{
									if ( f_label.isDisposed()){

										param.removeListener( this );
									}else{

										final String hyperlink = ((HyperlinkParameterImpl)param).getHyperlink();

										if (hyperlink != null) {

											Utils.execSWTThread(
												new Runnable()
												{
													@Override
													public void
													run()
													{
														LinkLabel.updateLinkedLabel(f_label, hyperlink);
													}
												});
										}
									}
								}
							});
					}

			}

			String	key = param.getKey();

			//System.out.println( "key = " + key );

			final Parameter	swt_param;

			if ( param instanceof BooleanParameterImpl ){

				if ( label == null ){

					swt_param = new BooleanParameter(current_composite, key,
							((BooleanParameterImpl) param).getDefaultValue(), param.getLabelKey());
				}else{

					swt_param = new BooleanParameter(current_composite, key, ((BooleanParameterImpl)param).getDefaultValue());
				}

				GridData data = new GridData();
				data.horizontalSpan = label==null?2:1;
				swt_param.setLayoutData(data);

				param.addListener(
					new ParameterListener()
					{
						@Override
						public void
						parameterChanged(
							com.biglybt.pif.ui.config.Parameter	p )
						{
							if ( swt_param.getControls()[0].isDisposed()){

								param.removeListener( this );

							}else{

								((BooleanParameter)swt_param).setSelected(((BooleanParameterImpl)param).getValue());
							}
						}
					});

			}else if ( param instanceof IntParameterImpl ){

				IntParameterImpl int_param = (IntParameterImpl)param;
				swt_param = new IntParameter(current_composite, key,
						int_param.getDefaultValue());

				if (int_param.isLimited()) {
					((IntParameter)swt_param).setMinimumValue(int_param.getMinValue());
					((IntParameter)swt_param).setMaximumValue(int_param.getMaxValue());
				}

				param.addListener(
						new ParameterListener()
						{
							@Override
							public void
							parameterChanged(
								com.biglybt.pif.ui.config.Parameter	p )
							{
								if ( swt_param.getControls()[0].isDisposed()){

									param.removeListener( this );

								}else{

									((IntParameter)swt_param).setValue(((IntParameterImpl)param).getValue());
								}
							}
						});


				GridData gridData = new GridData();
				gridData.widthHint = 100;

				swt_param.setLayoutData( gridData );

			}else if ( param instanceof ColorParameterImpl ) {
				final Composite area = new Composite(current_composite, SWT.NULL);
				//GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_HORIZONTAL );
				GridData gridData = new GridData();
				Utils.setLayoutData(area, gridData);
				layout = new GridLayout();
				layout.numColumns 	= 2;
				layout.marginHeight = 0;
				layout.marginWidth 	= 0;
				area.setLayout(layout);

				final ButtonParameter[] reset_button_holder = new ButtonParameter[1];
				final ColorParameterImpl color_param = (ColorParameterImpl)param;
				swt_param = new com.biglybt.ui.swt.config.ColorParameter(
						area, key, color_param.getRedValue(),
						color_param.getGreenValue(), color_param.getBlueValue()) {

					@Override
					public void newColorSet(RGB newColor) {
						color_param.reloadParamDataFromConfig(true);
						if (reset_button_holder[0] == null) {return;}
						reset_button_holder[0].getControl().setEnabled(true);
					}
				};

				// Reuse the same label as defined for UI reset buttons.
				reset_button_holder[0] = new ButtonParameter(area, "ConfigView.section.style.colorOverrides.reset");
				reset_button_holder[0].getControl().setEnabled(color_param.isOverridden());
				reset_button_holder[0].getControl().addListener(SWT.Selection, new Listener(){
					@Override
					public void handleEvent(Event event) {
						reset_button_holder[0].getControl().setEnabled(false);
						color_param.resetToDefault();
						color_param.reloadParamDataFromConfig(false);
					}
				});

				gridData = new GridData();
				gridData.widthHint = 50;

				swt_param.setLayoutData( gridData );
			}else if ( param instanceof StringParameterImpl ){

				GridData gridData = new GridData(GridData.FILL_HORIZONTAL);

				gridData.widthHint = 150;

				StringParameterImpl s_param = (StringParameterImpl)param;

				int	num_lines = s_param.getMultiLine();

				if ( num_lines <= 1 ){

					swt_param = new StringParameter(current_composite, key, s_param.getDefaultValue(), s_param.getGenerateIntermediateEvents());


					swt_param.setLayoutData( gridData );

				}else{

					StringAreaParameter sa_param =  new StringAreaParameter(current_composite, key, s_param.getDefaultValue());

					swt_param = sa_param;

					gridData.heightHint = sa_param.getPreferredHeight( num_lines );

					swt_param.setLayoutData( gridData );

				}
			}else if ( param instanceof InfoParameterImpl ){

				GridData gridData = new GridData(GridData.FILL_HORIZONTAL);

				gridData.widthHint = 150;

				swt_param = new InfoParameter(current_composite, key, "" );

				swt_param.setLayoutData( gridData );

			}else if ( param instanceof StringListParameterImpl ){

				StringListParameterImpl	sl_param = (StringListParameterImpl)param;

				GridData gridData = new GridData();

				gridData.widthHint = 150;

				swt_param = new StringListParameter(current_composite, key, sl_param.getDefaultValue(), sl_param.getLabels(), sl_param.getValues());

				swt_param.setLayoutData( gridData );

			}else if ( param instanceof PasswordParameterImpl ){

				GridData gridData = new GridData();

				gridData.widthHint = 150;

				swt_param = new PasswordParameter(current_composite, key, ((PasswordParameterImpl)param).getEncodingType());

				swt_param.setLayoutData( gridData );

			}else if ( param instanceof DirectoryParameterImpl || param instanceof com.biglybt.pifimpl.local.ui.config.FileParameter){

				Composite area = new Composite(current_composite, SWT.NULL);

				GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.FILL_HORIZONTAL );

				Utils.setLayoutData(area, gridData);

				layout = new GridLayout();

				layout.numColumns 	= 2;
				layout.marginHeight = 0;
				layout.marginWidth 	= 0;

				area.setLayout(layout);

				if (param instanceof DirectoryParameterImpl) {
					swt_param = new DirectoryParameter(area, key, ((DirectoryParameterImpl)param).getDefaultValue());
				}
				else {
					com.biglybt.pifimpl.local.ui.config.FileParameter fp = (com.biglybt.pifimpl.local.ui.config.FileParameter)param;
					swt_param = new com.biglybt.ui.swt.config.FileParameter(area, key, fp.getDefaultValue(), fp.getFileExtensions());
				}

			}else if ( param instanceof ActionParameterImpl ){

				ActionParameterImpl	_param = (ActionParameterImpl)param;

				if ( _param.getStyle() == ActionParameter.STYLE_BUTTON ){

					ButtonParameter bp = new ButtonParameter( current_composite, _param.getActionResource());

					swt_param = bp;

					buttons.add( bp.getButton());
				}else{

					swt_param = new LinkParameter( current_composite, _param.getActionResource());
				}

				swt_param.addChangeListener(
						new ParameterChangeAdapter()
						{
							@Override
							public void
							parameterChanged(
								Parameter	p,
								boolean		caused_internally )
							{
								try {
									param.parameterChanged( "" );
								} catch (Throwable t) {
									Debug.out(t);
								}
							}
						});
			} else if ( param instanceof UIParameterImpl) {
				if (((UIParameterImpl)param).getContext() instanceof UISWTParameterContext) {
					UISWTParameterContext context = (UISWTParameterContext)((UIParameterImpl)param).getContext();
					Composite internal_composite = new Composite(current_composite, SWT.NULL);
					GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
					Utils.setLayoutData(internal_composite, gridData);
					boolean initialised_component = true;
					try {context.create(internal_composite);}
					catch (Exception e) {Debug.printStackTrace(e); initialised_component = false;}

					if (initialised_component) {
						swt_param = new UISWTParameter(internal_composite, param.getKey());
					}
					else {
						swt_param = null;

						// We're only expecting this for plugins which screw up their component generation,
						// so we don't care that this text is not localised.
						if (label != null) {label.setText("Error while generating UI component.");}
					}
				}
				else {
					swt_param = null;
				}
			} else if ( param instanceof UITextAreaImpl ){

				swt_param = new TextAreaParameter(current_composite, ((UITextAreaImpl)param));

				GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan	= 2;
				gridData.heightHint = 100;
				swt_param.setLayoutData( gridData );

			}else{

					// label

				GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
				gridData.horizontalSpan	= 2;
				// for wrap to work

				gridData.widthHint = 300;

				Utils.setLayoutData(label,  gridData );

				swt_param	= null;
			}

			if ( swt_param == null ){

				if ( label == null ){
					comp_map.put( param, new Object[]{ null });
				}else{
					comp_map.put( param, new Object[]{ null, label });
				}

			}else{

				Control[]	c = swt_param.getControls();

				Object[] moo = new Object[c.length+(label==null?1:2)];

				int	pos = 1;

				moo[0] = swt_param;

				if ( label != null){
					moo[pos++] = label;
				}

				System.arraycopy(c, 0, moo, 0 + pos, c.length);

				comp_map.put( param, moo );
			}
		}

		if ( buttons.size() > 1 ){

			Utils.makeButtonsEqualWidth( buttons );
		}

		// Only need one instance
		ParameterImplListener parameterImplListener = new ParameterImplListener() {

			@Override
			public void enabledChanged(final ParameterImpl p) {
				final Object[] stuff = (Object[]) comp_map.get(p);

				if (stuff != null) {

					if (stuff[1] != null && ((Control) stuff[1]).isDisposed()) {
						// lazy tidyup
						p.removeImplListener(this);

					} else {

						Utils.execSWTThread(new AERunnable() {
							@Override
							public void runSupport() {
								{
									for (int k = 1; k < stuff.length; k++) {
										if (stuff[k] instanceof Control)
											((Control) stuff[k]).setEnabled(p.isEnabled());
									}

								}
							}
						});

					}
				}
			}

			@Override
			public void labelChanged(final ParameterImpl p, final String text,
			                         final boolean bIsKey) {
				final Object[] stuff = (Object[]) comp_map.get(p);

				if (stuff == null)
					return;

				Label lbl;
				if (stuff[1] instanceof Label)
					lbl = (Label) stuff[1];
				else if (stuff[0] instanceof Label)
					lbl = (Label) stuff[0];
				else
					return;


				if (lbl.isDisposed()) {
					// lazy tidyup
					p.removeImplListener(this);

				} else {
					final Label finalLabel = lbl;

					Utils.execSWTThread(new AERunnable() {
						@Override
						public void runSupport() {
							if (bIsKey)
								Messages.setLanguageText(finalLabel, text);
							else {
								finalLabel.setData("");
								finalLabel.setText(text);
							}
							finalLabel.getParent().layout(true);
						}
					});
				}
			}
		};

		for (int i=0;i<parameters.length;i++){

			final ParameterImpl	param = 	(ParameterImpl)parameters[i];

			param.addImplListener(parameterImplListener);

			if ( !param.isEnabled()){

			    Object[] stuff = (Object[])comp_map.get( param );

			    if ( stuff != null ){

				    for(int k = 1 ; k < stuff.length ; k++) {

				    	((Control)stuff[k]).setEnabled(false);
				    }
			    }
			}

			if ( !param.isVisible()){

			    Object[] stuff = (Object[])comp_map.get( param );

			    if ( stuff != null ){

				    for(int k = 1 ; k < stuff.length ; k++) {

				    	Control	con = (Control)stuff[k];

				    	con.setVisible(false);

				    	con.setSize( 0, 0 );

				    	GridData gridData = new GridData();

						gridData.heightHint 				= 0;
						gridData.verticalSpan				= 0;
						gridData.grabExcessVerticalSpace	= false;

						con.setLayoutData( gridData );
				    }
			    }
			}

			if ( param instanceof EnablerParameter ){

				List controlsToEnable = new ArrayList();

				Iterator iter = param.getEnabledOnSelectionParameters().iterator();

				while(iter.hasNext()){

					ParameterImpl enable_param = (ParameterImpl) iter.next();

				    Object[] stuff = (Object[])comp_map.get( enable_param );

				    if ( stuff != null ){

					    controlsToEnable.addAll(Arrays.asList(stuff).subList(1, stuff.length));
				    }
				}

				List controlsToDisable = new ArrayList();

				iter = param.getDisabledOnSelectionParameters().iterator();

				while(iter.hasNext()){

					ParameterImpl disable_param = (ParameterImpl)iter.next();

				    Object[] stuff = (Object[])comp_map.get( disable_param );

				    if ( stuff != null ){

					    controlsToDisable.addAll(Arrays.asList(stuff).subList(1, stuff.length));
				    }
				}

				Control[] ce = new Control[controlsToEnable.size()];
				Control[] cd = new Control[controlsToDisable.size()];

				if ( ce.length + cd.length > 0 ){

				    IAdditionalActionPerformer ap =
				    	new DualChangeSelectionActionPerformer(
				    			(Control[]) controlsToEnable.toArray(ce),
								(Control[]) controlsToDisable.toArray(cd));


				    Object[] data = (Object[])comp_map.get(param);

				    	// might not be visible (e.g. user mode too low) in which case it won't be in the map

				    if ( data != null ){

					    BooleanParameter	target = (BooleanParameter)data[0];

					    target.setAdditionalActionPerformer(ap);
				    }
				}
			}
		}

		return( main_tab );
	}
}
