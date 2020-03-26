/*
 * Copyright (C) Bigly Software.  All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.ui.config;

import java.util.ArrayList;
import java.util.List;

import com.biglybt.core.internat.MessageText;
import com.biglybt.core.security.*;
import com.biglybt.core.util.Base32;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.Wiki;
import com.biglybt.pifimpl.local.ui.config.*;
import com.biglybt.ui.UIFunctions;
import com.biglybt.ui.UIFunctionsManager;
import com.biglybt.ui.UIFunctionsUserPrompter;

import com.biglybt.pif.ui.config.ActionParameter;
import com.biglybt.pif.ui.config.ConfigSection;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.config.ParameterListener;

import static com.biglybt.core.config.ConfigKeys.Security.*;

public class ConfigSectionSecurity
	extends ConfigSectionImpl
{

	public static final String SECTION_ID = "security";

	private ConfigDetailsCallback cbCreateCert;

	private ConfigDetailsCallback cbBackupKeys;

	private ConfigDetailsCallback cbRestoreKeys;

	private CryptoManagerKeyListener cryptoManagerKeyListener;

	public ConfigSectionSecurity() {
		super(SECTION_ID, ConfigSection.SECTION_ROOT);
	}

	public void init(ConfigDetailsCallback cbCreateCert,
			ConfigDetailsCallback cbBackupKeys, ConfigDetailsCallback cbRestoreKeys) {
		this.cbCreateCert = cbCreateCert;
		this.cbBackupKeys = cbBackupKeys;
		this.cbRestoreKeys = cbRestoreKeys;
	}

	@Override
	public void deleteConfigSection() {
		super.deleteConfigSection();

		if (cryptoManagerKeyListener != null) {
			CryptoManagerFactory.getSingleton().removeKeyListener(
					cryptoManagerKeyListener);
			cryptoManagerKeyListener = null;
		}
	}

	@Override
	public void build() {

		// row

		if (cbCreateCert != null) {
			ActionParameterImpl cert_button = new ActionParameterImpl(
					"ConfigView.section.tracker.createcert",
					"ConfigView.section.tracker.createbutton");
			add(cert_button);
			cert_button.addListener(param -> cbCreateCert.run(mapPluginParams));
		}

		// row

		ActionParameterImpl reset_certs_button = new ActionParameterImpl(
				"ConfigView.section.security.resetcerts", "Button.reset");
		add(reset_certs_button);

		reset_certs_button.addListener(param -> {
			UIFunctions uiFunctions = UIFunctionsManager.getUIFunctions();
			if (uiFunctions == null) {
				SESecurityManager.resetTrustStore(false);
				return;
			}
			UIFunctionsUserPrompter prompter = uiFunctions.getUserPrompter(
					MessageText.getString(
							"ConfigView.section.security.resetcerts.warning.title"),
					MessageText.getString(
							"ConfigView.section.security.resetcerts.warning.msg"),
					new String[] {
						MessageText.getString("Button.ok"),
						MessageText.getString("Button.cancel")
			}, 1);
			if (prompter == null) {
				SESecurityManager.resetTrustStore(false);
				return;
			}
			prompter.setIconResource(UIFunctionsUserPrompter.ICON_WARNING);

			prompter.open(returnVal -> {
				if (returnVal != 0) {
					return;
				}

				if (SESecurityManager.resetTrustStore(false)) {

					UIFunctionsUserPrompter promptComplete = uiFunctions.getUserPrompter(
							MessageText.getString(
									"ConfigView.section.security.restart.title"),
							MessageText.getString("ConfigView.section.security.restart.msg"),
							new String[] {
								MessageText.getString("Button.ok")
					}, 0);

					if (promptComplete != null) {
						promptComplete.open(null);
					}

					uiFunctions.dispose(true);

				} else {

					UIFunctionsUserPrompter promptErr = uiFunctions.getUserPrompter(
							MessageText.getString(
									"ConfigView.section.security.resetcerts.error.title"),
							MessageText.getString(
									"ConfigView.section.security.resetcerts.error.msg"),
							new String[] {
								MessageText.getString("Button.ok")
					}, 0);
					if (promptErr != null) {
						promptErr.setIconResource(UIFunctionsUserPrompter.ICON_ERROR);
						promptErr.open(null);
					}
				}
			});

		});

		reset_certs_button.setEnabled(SESecurityManager.resetTrustStore(true));

		// row

		BooleanParameterImpl auto_install = add(new BooleanParameterImpl(BCFG_SECURITY_CERT_AUTO_INSTALL, "security.cert.auto.install"));

		// row

		BooleanParameterImpl auto_decline =  add(new BooleanParameterImpl(BCFG_SECURITY_CERT_AUTO_DECLINE, "security.cert.auto.decline"));
		
		ParameterListener pl = (n)->{
			
			if ( auto_install.getValue() && auto_decline.getValue()){
					// shouldn't have both set...
				auto_decline.setValue( false );
			}
			if (auto_install.getValue()){
				auto_decline.setEnabled(false);
			}else{
				auto_decline.setEnabled(true);
			}
			
			if (auto_decline.getValue()){
				auto_install.setEnabled(false);
			}else{
				auto_install.setEnabled(true);
			}
		};
		
		pl.parameterChanged( null );
		
		auto_install.addListener( pl );
		auto_decline.addListener( pl );
		
		// row

		add(new LabelParameterImpl("ConfigView.section.security.toolsinfo"));

		// row

		DirectoryParameterImpl pathParameter = new DirectoryParameterImpl(
				SCFG_SECURITY_JAR_TOOLS_DIR, "ConfigView.section.security.toolsdir");
		add(pathParameter);
		pathParameter.setDialogTitleKey(
				"ConfigView.section.security.choosetoolssavedir");

		// row

		ActionParameterImpl pw_button = new ActionParameterImpl(
				"ConfigView.section.security.clearpasswords",
				"ConfigView.section.security.clearpasswords.button");
		add(pw_button);

		pw_button.addListener(param -> {
			SESecurityManager.clearPasswords();

			CryptoManagerFactory.getSingleton().clearPasswords();
		});

		CryptoManager crypt_man = CryptoManagerFactory.getSingleton();
		List<Parameter> listCrypto = new ArrayList<>();

		// wiki link

		add(new HyperlinkParameterImpl("ConfigView.label.please.visit.here",
				Wiki.PUBLIC_PRIVATE_KEYS), listCrypto);

		// publick key display

		byte[] public_key = crypt_man.getECCHandler().peekPublicKey();

		String key = public_key == null
				? MessageText.getString("ConfigView.section.security.publickey.undef")
				: Base32.encode(public_key);

		ActionParameterImpl paramPublicKey = new ActionParameterImpl(
				"ConfigView.section.security.publickey", "!" + key + "!");
		add(paramPublicKey, listCrypto);
		paramPublicKey.setStyle(ActionParameter.STYLE_LINK);
		// TODO: Tooltip
		// Messages.setLanguageText(public_key_value, "ConfigView.copy.to.clipboard.tooltip", true);
		paramPublicKey.addListener(param -> {
			UIFunctions uif = UIFunctionsManager.getUIFunctions();
			if (uif == null) {
				return;
			}
			uif.copyToClipboard(key);
		});

		cryptoManagerKeyListener = new CryptoManagerKeyListener() {
			@Override
			public void keyChanged(final CryptoHandler handler) {
				if (handler.getType() == CryptoManager.HANDLER_ECC) {

					byte[] public_key = handler.peekPublicKey();

					String key = public_key == null
							? MessageText.getString(
									"ConfigView.section.security.publickey.undef")
							: Base32.encode(public_key);
					paramPublicKey.setActionResource("!" + key + "!");
				}
			}

			@Override
			public void keyLockStatusChanged(CryptoHandler handler) {
			}
		};
		crypt_man.addKeyListener(cryptoManagerKeyListener);

		// manage keys

		//
		//		    final BooleanParameter manage_keys = new BooleanParameter(
		//		    		crypto_group, "crypto.keys.system.managed.temp",
		//		    		"ConfigView.section.security.system.managed");
		//
		//		    manage_keys.setLayoutData( gridData );
		//
		//		    final ClientCryptoManager crypto_man 	= CryptoManagerFactory.getSingleton();
		//			final CryptoHandler ecc_handler = crypto_man.getECCHandler();
		//
		//		    manage_keys.setSelected(
		//		    		ecc_handler.getDefaultPasswordHandlerType() == CryptoManagerPasswordHandler.HANDLER_TYPE_SYSTEM );
		//
		//
		//		    manage_keys.addChangeListener(
		//		    	new ParameterChangeListener ()
		//		    	{
		//		    		public void
		//		    		parameterChanged(
		//		    			Parameter 	p,
		//		    			boolean 	caused_internally )
		//		    		{
		//	    				boolean existing_value = ecc_handler.getDefaultPasswordHandlerType() == CryptoManagerPasswordHandler.HANDLER_TYPE_SYSTEM;
		//
		//	    				if ( existing_value == manage_keys.isSelected()){
		//
		//	    					return;
		//	    				}
		//
		//	    				String	error = null;
		//
		//	    				int	new_type = manage_keys.isSelected()?CryptoManagerPasswordHandler.HANDLER_TYPE_SYSTEM:CryptoManagerPasswordHandler.HANDLER_TYPE_USER;
		//
		//    					try{
		//    						ecc_handler.setDefaultPasswordHandlerType( new_type );
		//
		//    						error = null;
		//
		//    					}catch( CryptoManagerPasswordException e ){
		//
		//    						if ( e.wasIncorrect()){
		//
		//    							error = MessageText.getString( "ConfigView.section.security.unlockkey.error" );
		//
		//    						}else{
		//
		//    							if ( existing_value || !ecc_handler.isUnlocked()){
		//
		//    								error = MessageText.getString( "Torrent.create.progress.cancelled" );
		//
		//    							}else{
		//
		//    								error = MessageText.getString( "ConfigView.section.security.vuze.login" );
		//    							}
		//    						}
		//    					}catch( Throwable e ){
		//
		//    						error = Debug.getNestedExceptionMessage( e );
		//    					}
		//
		//	    				if ( error != null ){
		//
		//	    					MessageBoxShell mb = new MessageBoxShell(
		//	    							SWT.ICON_ERROR | SWT.OK,
		//	    							MessageText.getString("ConfigView.section.security.op.error.title"),
		//	    							MessageText.getString("ConfigView.section.security.op.error",
		//	    									new String[] { error }));
		//	      				mb.setParent(parent.getShell());
		//	    					mb.open(null);
		//	    				}
		//
		//	    				boolean new_value = ecc_handler.getDefaultPasswordHandlerType() == CryptoManagerPasswordHandler.HANDLER_TYPE_SYSTEM;
		//
		//	    				if ( new_value != manage_keys.isSelected()){
		//
		//	    					manage_keys.setSelected( new_value );
		//	    				}
		//		    		}
		//		    	});

		// reset keys

		ActionParameterImpl reset_key_button = new ActionParameterImpl(
				"ConfigView.section.security.resetkey",
				"ConfigView.section.security.clearpasswords.button");
		add(reset_key_button, listCrypto);

		reset_key_button.addListener(param -> {
			UIFunctions uif = UIFunctionsManager.getUIFunctions();
			if (uif != null) {

				UIFunctionsUserPrompter prompter = uif.getUserPrompter(
						MessageText.getString(
								"ConfigView.section.security.resetkey.warning.title"),
						MessageText.getString(
								"ConfigView.section.security.resetkey.warning"),
						new String[] {
							MessageText.getString("Button.ok"),
							MessageText.getString("Button.cancel")
				}, 1);
				if (prompter != null) {
					prompter.setIconResource(UIFunctionsUserPrompter.ICON_WARNING);

					prompter.open(returnVal -> {
						if (returnVal != 0) {
							return;
						}

						try {
							crypt_man.getECCHandler().resetKeys("Manual key reset");

						} catch (Throwable e) {

							UIFunctionsUserPrompter warningPrompt = uif.getUserPrompter(
									MessageText.getString(
											"ConfigView.section.security.resetkey.error.title"),
									getError(e), new String[] {
										MessageText.getString("Button.ok")
							}, 0);
							if (warningPrompt != null) {
								warningPrompt.setIconResource(
										UIFunctionsUserPrompter.ICON_ERROR);
								warningPrompt.open(null);
							}
						}
					});
				}
				return;
			}

			// fallback
			try {
				crypt_man.getECCHandler().resetKeys("Manual key reset (no prompt)");

			} catch (Throwable e) {
				Debug.out(e);
			}
		});

		// unlock

		ActionParameterImpl priv_key_button = new ActionParameterImpl(
				"ConfigView.section.security.unlockkey",
				"ConfigView.section.security.unlockkey.button");
		add(priv_key_button, listCrypto);
		priv_key_button.addListener(param -> {
			try {
				crypt_man.getECCHandler().getEncryptedPrivateKey("Manual unlock");

			} catch (Throwable e) {
				UIFunctions uif = UIFunctionsManager.getUIFunctions();

				if (uif == null) {
					Debug.out(e);
				} else {

					UIFunctionsUserPrompter warningPrompt = uif.getUserPrompter(
							MessageText.getString(
									"ConfigView.section.security.resetkey.error.title"),
							getError(e), new String[] {
								MessageText.getString("Button.ok")
					}, 0);
					if (warningPrompt != null) {
						warningPrompt.setIconResource(UIFunctionsUserPrompter.ICON_ERROR);
						warningPrompt.open(null);
					}
				}

			}

		});

		// backup

		if (cbBackupKeys != null) {
			ActionParameterImpl backup_keys_button = new ActionParameterImpl(
					"ConfigView.section.security.backupkeys",
					"ConfigView.section.security.backupkeys.button");
			add(backup_keys_button, listCrypto);
			backup_keys_button.addListener(
					param -> cbBackupKeys.run(mapPluginParams));
		}

		// restore

		if (cbRestoreKeys != null) {
			ActionParameterImpl restore_keys_button = new ActionParameterImpl(
					"ConfigView.section.security.restorekeys",
					"ConfigView.section.security.restorekeys.button");
			add(restore_keys_button, listCrypto);
			reset_certs_button.addListener(
					param -> cbRestoreKeys.run(mapPluginParams));
		}

		add("pgCrypto", new ParameterGroupImpl(
				"ConfigView.section.security.group.crypto", listCrypto));
	}

	public static String getError(Throwable e) {
		String error;

		if (e instanceof CryptoManagerPasswordException) {

			if (((CryptoManagerPasswordException) e).wasIncorrect()) {

				error = MessageText.getString(
						"ConfigView.section.security.unlockkey.error");

			} else {

				final CryptoManager crypto_man = CryptoManagerFactory.getSingleton();
				final CryptoHandler ecc_handler = crypto_man.getECCHandler();

				//if ( ecc_handler.getDefaultPasswordHandlerType() == CryptoManagerPasswordHandler.HANDLER_TYPE_SYSTEM ){
				//
				//	error = MessageText.getString( "ConfigView.section.security.nopw_v" );
				//
				//}else{

				error = MessageText.getString("ConfigView.section.security.nopw");
				//}
			}
		} else {

			error = MessageText.getString(
					"ConfigView.section.security.resetkey.error") + ": "
					+ Debug.getNestedExceptionMessage(e);
		}

		return (error);
	}

}
