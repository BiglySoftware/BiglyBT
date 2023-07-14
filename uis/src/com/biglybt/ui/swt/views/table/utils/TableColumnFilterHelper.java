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

package com.biglybt.ui.swt.views.table.utils;

import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

import com.biglybt.core.util.DisplayFormatters;
import com.biglybt.core.util.GeneralUtils;
import com.biglybt.core.util.RegExUtil;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.ui.common.table.TableCellCore;
import com.biglybt.ui.swt.views.table.TableRowSWT;
import com.biglybt.ui.swt.views.table.TableViewSWT;

public class 
TableColumnFilterHelper<T>
{
	private static final int MM_EQ	 	= 0;
	private static final int MM_GT		= 1;
	private static final int MM_GE		= 2;
	private static final int MM_LT		= 3;
	private static final int MM_LE		= 4;
	
	private final TableViewSWT<T>	table_view;
	private final String			regex_key;
	
	private TimerEventPeriodic 	filter_refilter;
	private Object				refilter_lock = this;

	private volatile boolean 	filter_active;
	
	private TableColumn		col_cache;
	private String			col_cache_name;

	private String			date_cache_str;
	private double			date_cache_time;
	
	private int match_mode;
	
	public
	TableColumnFilterHelper(
		TableViewSWT<T>		_table_view,
		String				_regex_key )
	{
		table_view	= _table_view;
		regex_key	= _regex_key;
	}
	
	public void
	filterSet(
		String		filter )
	{
		synchronized( refilter_lock ){
			
			if ( filter.isEmpty()){
				
				if ( filter_refilter != null ){
					
					filter_refilter.cancel();
					
					filter_refilter = null;
				}
			}else if ( filter_refilter == null ){
				
				filter_refilter = SimpleTimer.addPeriodicEvent(
					"tcfh:refilter", 5000,
					(ev)->{
						if ( table_view.isDisposed()){
							
							synchronized( refilter_lock ){
								
								if ( filter_refilter != null ){
									
									filter_refilter.cancel();
									
									filter_refilter = null;
								}
								
								return;
							}
						}else if ( filter_active ){
															
							table_view.refilter();
						}
					});
			}
		}
	}
	
	public boolean
	filterCheck(
		T			data_source,
		String		original_filter,
		boolean		regex,
		String		default_match_text,
		boolean		ignore_column_match )
	{
		if ( original_filter.isEmpty()){
			
			return( true );
		}

		String	filter_text = null;
		String	match_text 	= null;
	
		double match_numeric = Double.NaN;
		
		int pos = original_filter.indexOf( ':' );
		
		if ( pos != -1 && !ignore_column_match ){
			
			String col_name 	= original_filter.substring( 0, pos ).trim();
			String col_value	= original_filter.substring( pos+1 ).trim();
						
			if ( col_value.startsWith( ">" )){
			
				if ( col_value.startsWith( ">=" )){
					
					match_mode = MM_GE;
					
					col_value = col_value.substring(2);
					
				}else{
				
					match_mode = MM_GT;
					
					col_value = col_value.substring(1);
				}
			}else if ( col_value.startsWith( "<" )){
				
				if ( col_value.startsWith( "<=" )){
				
					match_mode = MM_LE;
					
					col_value = col_value.substring(2);
					
				}else{
				
					match_mode = MM_LT;
					
					col_value = col_value.substring(1);
				}
			}else{
				
				if ( col_value.startsWith( "=" )){
					
					col_value = col_value.substring(1);
				}
				
				match_mode = MM_EQ;
			}
			
			filter_text = col_value;
			
			TableColumn col;
			
			synchronized( refilter_lock ){
				
				if ( col_cache_name == null || !col_cache_name.equals( col_name )){
					
					col_cache = table_view.getTableColumn( col_name, true );
					
					col_cache_name = col_name;
				}
				
				col = col_cache;
			}
		
			if ( col != null ){

				match_text = "";
				
				TableRowSWT row = table_view.getRowSWT( data_source );
				
				boolean is_fake = false;
				
				if ( row == null ){
					
						// row may not be visible (either just adding or already filtered)
					
					row = table_view.createFakeRow( data_source );
					
					is_fake = true;
				}
				
				try{
						// ensure cells are constructed
					
					row.setShown( true, true );
					
					TableCellCore cell = (TableCellCore)row.getTableCell(  col );
					
					if ( cell != null ){
					
							// pick up latest value
						
						cell.refresh();
											
						match_text = cell.getTextEquivalent();
						
						if ( match_text == null ){
							
							match_text = cell.getText();
						}
						
						match_numeric = cell.getNumeric();
					}
				}finally{
					
					if ( is_fake ){
						
						row.delete();
					}
				}
			}
		}
		
		boolean active;
		
		if ( match_text == null || filter_text == null ){
			
			filter_text	= original_filter;
			match_text	= default_match_text;
			
			active = false;
			
		}else{
			
			active = true;
		}
				
		if ( filter_active != active ){
			
			filter_active = active;
		}
		
		if ( active && match_mode != MM_EQ ){
			
			double filter_num;
			
			if ( filter_text.split( "/" ).length == 3 ){
			
				filter_num = Double.NaN;

				if ( date_cache_str != null && date_cache_str.equals( filter_text )){
					
					filter_num = date_cache_time;
					
				}else{
					
					date_cache_str = filter_text;
					
					try{
						filter_num = date_cache_time = new SimpleDateFormat( "yyyy/MM/dd" ).parse( filter_text ).getTime();
											 
					}catch( Throwable e ){
						
						date_cache_time = Double.NaN;
					}
				}
			}else{
						
				filter_num 	= getNumber( filter_text );
			}
			
			double match_num	= Double.isNaN(match_numeric)?getNumber( match_text ):match_numeric;
			
			if (  Double.isNaN( filter_num ) ||  Double.isNaN( match_num )){
				
				return( false );
				
			}else{
				switch( match_mode ){
					case MM_GT:return( match_num > filter_num );
					case MM_GE:return( match_num >= filter_num );
					case MM_LT:return( match_num < filter_num );
					case MM_LE:return( match_num <= filter_num );
					
					default:return( false );
				}
			}
		}else{
			
			boolean	match_result = true;

			String expr;
			
			if ( regex ){
				
				expr = filter_text;
				
				if ( expr.startsWith( "!" )){
					
					expr = expr.substring(1);

					match_result = false;
				}
			}else{
				
				expr = RegExUtil.convertAndOrToExpr( filter_text );
			}
	
			Pattern pattern = RegExUtil.getCachedPattern( regex_key, expr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE );
	
			return( pattern.matcher(match_text).find() == match_result );
		}
	}
	
	private double
	getNumber(
		String	str )
	{
		str = str.trim();
		
		char[] chars = str.toCharArray();
		
		try{
			char sep = DisplayFormatters.getDecimalSeparator();
			
			for ( int i=0;i<chars.length;i++){
				
				char c = chars[i];
				
				if ( c != sep && !Character.isDigit( c )){
					
					String num 	= str.substring(0,i);
					String unit = str.substring(i).trim();
					
					long mult = GeneralUtils.getUnitMultiplier(unit, true );
					
					if ( mult <= 0 ){
						
						mult = 1;	// treat invalid unit as extraneous text
					}
					
					return( Double.parseDouble( num ) * mult );
				}
			}
			
			return( Double.parseDouble( str ));
			
		}catch( Throwable e ){
			
			return( Double.NaN );
		}
	}
}
