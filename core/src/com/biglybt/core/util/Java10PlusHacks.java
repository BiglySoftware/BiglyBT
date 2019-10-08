/*
 * Copyright (C) Bigly Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */

package com.biglybt.core.util;

import java.io.File;
import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;

public class 
Java10PlusHacks
{
	private static Java10PlusHacksImpl	impl;
	
	private static final boolean	hack = true;
	
	static{
		if ( hack ){
			
			try{
				byte[] bytes = Base32.decode( "ZL7LVPQAAAADGADEA4AAEAIAFJRW63JPMJUWO3DZMJ2C6Y3POJSS65LUNFWC6STBOZQTCMCQNR2XGSDBMNVXGJCJNVYGYBYAAQAQAEDKMF3GCL3MMFXGOL2PMJVGKY3UA4AAMAIAHFRW63JPMJUWO3DZMJ2C6Y3POJSS65LUNFWC6STBOZQTCMCQNR2XGSDBMNVXGJCKMF3GCMJQKBWHK42IMFRWW42JNVYGYAIABR3GQX3NN5SGSZTJMVZHGAIADRGGUYLWMEXWYYLOM4XWS3TWN5VWKL2WMFZEQYLOMRWGKOYBAAIHM2C7MZUWK3DEIFRWGZLTONXXEAIADB3GQX3POZSXE4TJMRSUM2LFNRSECY3DMVZXG33SAEAAO5TIL5ZG633UAEAAMPDJNZUXIPQBAABSQKKWAEAAIQ3PMRSQUAADAAIAYAAMAAGQOAASAEABO2TBOZQS63DBNZTS64TFMZWGKY3UF5DGSZLMMQFAAFAACYDQAFIBAAPGUYLWMEXWYYLOM4XWS3TWN5VWKL2NMV2GQ33EJBQW4ZDMMVZQYAAXAAMACAAGNRXW623VOAAQAKJIFFGGUYLWMEXWYYLOM4XWS3TWN5VWKL2NMV2GQ33EJBQW4ZDMMVZSITDPN5VXK4B3BIABIAA2BQABWAA4AEAA64DSNF3GC5DFJRXW623VOBEW4AIAMEUEY2TBOZQS63DBNZTS6Q3MMFZXGO2MNJQXMYJPNRQW4ZZPNFXHM33LMUXU2ZLUNBXWISDBNZSGYZLTERGG633LOVYDWKKMNJQXMYJPNRQW4ZZPNFXHM33LMUXU2ZLUNBXWISDBNZSGYZLTERGG633LOVYDWCAADYAQACLNN5SGSZTJMVZHGCIAEAACEBYAEEAQAELKMF3GCL3MMFXGOL2JNZ2GKZ3FOIGAAIYAEQAQABCULFIEKAIACFGGUYLWMEXWYYLOM4XUG3DBONZTWCQAEYACQBYAE4AQAJLKMF3GCL3MMFXGOL3JNZ3G623FF5GWK5DIN5SEQYLOMRWGK4ZEJRXW623VOAGAAKIAFIAQADLGNFXGIVTBOJEGC3TENRSQCACSFBGGUYLWMEXWYYLOM4XUG3DBONZTWTDKMF3GCL3MMFXGOL2TORZGS3THHNGGUYLWMEXWYYLOM4XUG3DBONZTWKKMNJQXMYJPNRQW4ZZPNFXHM33LMUXVMYLSJBQW4ZDMMU5QSAABAAWAYAAHAAEAQABOAEACE2TENMXGS3TUMVZG4YLMFZZGKZTMMVRXILSGNFSWYZCBMNRWK43TN5ZAUABQAAZAOABRAEAA62TBOZQS63DBNZTS6Q3MMFZXGDAAGMADIAIAA5TG64SOMFWWKAIAEUUEY2TBOZQS63DBNZTS6U3UOJUW4ZZ3FFGGUYLWMEXWYYLOM4XUG3DBONZTWCAAGYAQADLGNFSWYZCBMNRWK43TN5ZASAABAA4AYAAJAAEAQAB2AEABK33WMVZHE2LEMVDGSZLMMRAWGY3FONZW64QJAAAQAPAMAAFAACAIAA7ACAAEOJXW65AJAAAQAQAMAAFQACAKABBAARAHABBQCAATNJQXMYJPNRQW4ZZPKRUHE33XMFRGYZIMABCQADIBAAHXA4TJNZ2FG5DBMNVVI4TBMNSQCAAPJRUW4ZKOOVWWEZLSKRQWE3DFAEABETDPMNQWYVTBOJUWCYTMMVKGCYTMMUAQABDUNBUXGAIAFRGGG33NF5RGSZ3MPFRHIL3DN5ZGKL3VORUWYL2KMF3GCMJQKBWHK42IMFRWW4ZEJFWXA3B3AEACOTDKMF3GCL3MMFXGOL3JNZ3G623FF5GWK5DIN5SEQYLOMRWGK4ZEJRXW623VOA5QCAAQMNWF6ZTJMVWGIQLDMNSXG43POIAQAALFAEABKTDKMF3GCL3MMFXGOL2UNBZG653BMJWGKOYBAAGVG5DBMNVU2YLQKRQWE3DFAEABC43FORDGSZLMMRGW6ZDJMZUWK4TTAEAB2KCMNJQXMYJPNRQW4ZZPOJSWM3DFMN2C6RTJMVWGIO2JFFLAUACSABKAOACTAEABU2TBOZQS63DBNZTS62LOOZXWWZJPKZQXESDBNZSGYZIMABKQAUABAABXGZLUBIAFEACXBQAFKACYAEACYKCMNJQXMYJPNRQW4ZZPOJSWM3DFMN2C6RTJMVWGIO2MNJQXMYJPNRQW4ZZPKZXWSZB3FFLACAAFMZUWK3DEAEABSTDKMF3GCL3MMFXGOL3SMVTGYZLDOQXUM2LFNRSDWAIAAFEQCAAKKNXXK4TDMVDGS3DFAEABISTBOZQTCMCQNR2XGSDBMNVXGLTKMF3GCAIABREW43TFOJBWYYLTONSXGBYAMAAQAJLDN5WS6YTJM5WHSYTUF5RW64TFF52XI2LMF5FGC5TBGEYFA3DVONEGCY3LOMAQABCJNVYGYAIACNFGC5TBGEYFA3DVONEGCY3LONEW24DMAEAAMTDPN5VXK4AAEEAACAADAAAQABIAAQAAEAAHAAEAAAAAAIAASAAIAAAAAAQABIAAQAAAAABAACYABAAAAAACAAAQADAABUAACAAOAAAABXIAAUAAGAAAABJSVNYAB4JBDOAACO4AAGKMFIVREEISDWZAAH5WAAS3KABLCIW3QABPJUVCWEQRCI2SZNQAEW2QANZKFMJBCERZFS3AAJNVAA5SUKYSCEJD2EQRWYACLNIAH6TQACCMFO3AAQNRAAAQABAAJIAE2ACCAABQARQAAAACUAAKAAAAAVQAAQAFSAANABNQAHAALUACEAC7AAXQAYIAHQAGGACKABSQATQAM4AFEADJABDQAAAAFIAAIAAAABJQASAAJEAAAAANAA6QAFYAJIAACABCAAUAASYAEQAAEACOAACAATAAJUAACACOAAAAAEAAAL7QATIAAEDQAAIAAEDQAQQEAAAQATYAKAAACAAOAAAAA4YAAMAAGAAAAASSVNAAFMVRZNQAKEVLIABXFMA3MACWFK2AAOZLAG3AAVRKWQAD6KYBWYAFNMIAAAAAEACGAAAAAFQAAUAAAADQAAEQA4QACIAHGAA3AB2AAJAAOUAEOAAAAAQAAAYAAAACKACIABEQAAAAAAACKACZABNAAAIAAAACKAA6ABNQAAQAAIAFYAAAAABAAXIALYAAAAA2AABQAAIAL4AGCAAJAACQAXYAMIDASABGAAKAAYYADE" ); 
						
				class HackClassLoader
					extends ClassLoader
				{
					public Class<Java10PlusHacksImpl>
					loadClass(
						String 		name,
						byte[] 		bytes )
					{
						Class<Java10PlusHacksImpl> cla = (Class<Java10PlusHacksImpl>)defineClass( name, bytes, 0, bytes.length );
			
						resolveClass( cla );
			
						return( cla );
					}
				}
			
				Class<Java10PlusHacksImpl> cla =
					new HackClassLoader().loadClass(
						"com.biglybt.core.util.Java10PlusHacks$Impl",
						bytes );
			
				impl = cla.getDeclaredConstructor().newInstance();
				
			}catch( Throwable e ){
				
				e.printStackTrace();
			}
		}else{
			
			//impl = new Impl();
		}
	}
	
	// This is the code loaded above...
	 
	/*
	public static class
	Impl
		implements Java10PlusHacksImpl
	{
		private VarHandle vh_modifiers;
		private VarHandle vh_fieldAccessor;
		private VarHandle vh_overrideFieldAccessor;
		private VarHandle vh_root;

		public
		Impl()
		{
			try{
				Lookup lookup = MethodHandles.privateLookupIn(Field.class, MethodHandles.lookup());
				
				vh_modifiers = lookup.findVarHandle( Field.class, "modifiers", int.class );
				
				Class cl_fieldAccessor = Class.forName( "jdk.internal.reflect.FieldAccessor" );
				
				vh_fieldAccessor = lookup.findVarHandle( Field.class, "fieldAccessor", cl_fieldAccessor );
				
				vh_overrideFieldAccessor = lookup.findVarHandle( Field.class, "overrideFieldAccessor", cl_fieldAccessor );

				vh_root = lookup.findVarHandle( Field.class, "root", Field.class );
				
			}catch ( Throwable e ){
				
				e.printStackTrace();
			}			
		}
		
		public void
		setFieldModifiers(
			Field		field,
			int			modifiers )
		{
			vh_modifiers.set( field, modifiers );
			
			vh_fieldAccessor.set( field, null );
			vh_overrideFieldAccessor.set( field, null );
			vh_root.set( field, null );
		}
	}
	*/
	
	public static void
	setFieldModifiers(
		Field		field,
		int			modifiers )
	{
		impl.setFieldModifiers( field, modifiers );
	}
	
	public static void
	setFieldNonFinal(
		Field		field )
	{
		int mods = field.getModifiers();
		
        if ( Modifier.isFinal (mods )){
        	
        	setFieldModifiers( field, mods & ~Modifier.FINAL );
        }
	}
	
	public interface
	Java10PlusHacksImpl
	{
		public void
		setFieldModifiers(
			Field		field,
			int			modifiers );
	}
	
	public static void
	main(
		String[] args )
	{
		try{

			Field field = InetAddress.class.getDeclaredField( "preferIPv6Address" );
			
			field.setAccessible( true );
			
			setFieldNonFinal( field );

			field.set( null,  1);
			
		}catch( Throwable e ){

			e.printStackTrace();
		}
		
		if ( !hack ){
			
			try{
				File cla = new File( "com\\biglybt\\core\\util\\Java10PlusHacks$Impl.class");
				
				byte[] bytes = FileUtil.readFileAsByteArray( cla );
				
				System.out.println( Base32.encode( bytes ));
				
			}catch( Throwable e ){
				
				e.printStackTrace();
			}
		}
	}
}
