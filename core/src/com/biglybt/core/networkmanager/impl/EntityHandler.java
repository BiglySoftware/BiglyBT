/*
 * Created on Sep 23, 2004
 * Created by Alon Rohter
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

package com.biglybt.core.networkmanager.impl;

import java.util.HashMap;

import com.biglybt.core.networkmanager.NetworkConnectionBase;
import com.biglybt.core.networkmanager.NetworkManager;
import com.biglybt.core.networkmanager.RateHandler;
import com.biglybt.core.util.AEMonitor;
import com.biglybt.core.util.Debug;


/**
 * Manages transfer entities on behalf of peer connections.
 * Each entity handler has a global pool which manages all
 * connections by default.  Connections can also be "upgraded"
 * to a higher connection control level, i.e. each connection
 * has its own specialized entity for performance purposes.
 */
public class EntityHandler {
  private final HashMap upgraded_connections = new HashMap();
  private final AEMonitor lock = new AEMonitor( "EntityHandler" );
  private final MultiPeerUploader global_uploader;
  private final MultiPeerDownloader2 global_downloader;
  private boolean global_registered = false;
  private final int handler_type;

  private final NetworkManager net_man;
  
  /**
   * Create a new entity handler using the given rate handler.
   * @param type read or write type handler
   * @param rate_handler global max rate handler
   */
  public 
  EntityHandler( 
	NetworkManager		_net_man,
	int 				type, 
	RateHandler 		rate_handler ) 
  {
	net_man		= _net_man;
	
    this.handler_type = type;
    if( handler_type == TransferProcessor.TYPE_UPLOAD ) {
      global_uploader = new MultiPeerUploader( rate_handler );
      global_downloader = null;
    }
    else {  //download type
      global_downloader = new MultiPeerDownloader2( rate_handler );
      global_uploader = null;
    }
  }



  /**
   * Register a peer connection for management by the handler.
   * @param connection to add to the global pool
   */
  public void 
  registerPeerConnection( NetworkConnectionBase connection ) 
  {
	  try{  
		  lock.enter();
		  
		  if ( !global_registered ){
			  
			  if ( handler_type == TransferProcessor.TYPE_UPLOAD ){
				  
				  net_man.addWriteEntity( global_uploader, -1 );  //register global upload entity
				  
			  }else{
				  
				  net_man.addReadEntity( global_downloader, -1 );  //register global download entity
			  }

			  global_registered = true;
		  }

		  if ( handler_type == TransferProcessor.TYPE_UPLOAD ) {

			  global_uploader.addPeerConnection( connection );

		  }else{

			  global_downloader.addPeerConnection( connection );
		  }
	  }
	  finally{

		  lock.exit(); 
	  }
  }


  /**
   * Remove a peer connection from the entity handler.
   * @param connection to cancel
   */
  public boolean 
  cancelPeerConnection( 
	NetworkConnectionBase connection ) 
  {
	  try{  
		  lock.enter();
		  
		  if ( connection == null ){
			  
			  Debug.out( "connection is null" );
			  
			  return( false );
		  }
		  
		  if ( handler_type == TransferProcessor.TYPE_UPLOAD ){
			  
			  if ( !global_uploader.removePeerConnection( connection )){  //if not found in the pool entity
				  
				  SinglePeerUploader upload_entity = (SinglePeerUploader)upgraded_connections.remove( connection );  //check for it in the upgraded list
				  
				  if ( upload_entity != null ){
					  
					  return( net_man.removeWriteEntity( upload_entity ));  //cancel from write processing
				  }
			  }else{
				  
				  return( true );
			  }
		  }
		  else {
			  if ( !global_downloader.removePeerConnection( connection ) ) {  //if not found in the pool entity
				  
				  SinglePeerDownloader download_entity = (SinglePeerDownloader)upgraded_connections.remove( connection );  //check for it in the upgraded list
				  
				  if ( download_entity != null ){
					  
					  return( net_man.removeReadEntity( download_entity ));  //cancel from read processing
				  }
			  }else{
				  
				  return( true );
			  }
		  }

		  return( false );
		  
	  }finally{
		  
		  lock.exit();  
	  }
  }


  /**
   * Upgrade a peer connection from the general pool to its own high-speed entity.
   * @param connection to upgrade from global management
   * @param handler individual connection rate handler
   */
  public void 
  upgradePeerConnection( 
	NetworkConnectionBase	connection, 
	RateHandler				handler, 
	int						partition_id ) 
  {
	  try{  
		  lock.enter();
		  
		  if ( connection == null ){
			  
			  Debug.out( "connection is null" );
			  
			  return;
		  }
		  
		  if( handler_type == TransferProcessor.TYPE_UPLOAD ) {
			  SinglePeerUploader upload_entity = new SinglePeerUploader( connection, handler );
			  if( !global_uploader.removePeerConnection( connection ) ) {  //remove it from the general upload pool
				  Debug.out( "upgradePeerConnection:: upload entity not found/removed !" );
			  }
			  net_man.addWriteEntity( upload_entity, partition_id );  //register it for write processing
			  upgraded_connections.put( connection, upload_entity );  //add it to the upgraded list
		  }
		  else {
			  SinglePeerDownloader download_entity = new SinglePeerDownloader( connection, handler );
			  if( !global_downloader.removePeerConnection( connection ) ) {  //remove it from the general upload pool
				  Debug.out( "upgradePeerConnection:: download entity not found/removed !" );
			  }
			  net_man.addReadEntity( download_entity, partition_id );  //register it for read processing
			  upgraded_connections.put( connection, download_entity );  //add it to the upgraded list
		  }
	  }finally{
		  lock.exit();  
	  }
  }


  /**
   * Downgrade (return) a peer connection back into the general pool.
   * @param connection to downgrade back into the global entity
   */
  public void 
  downgradePeerConnection( 
	NetworkConnectionBase connection ) 
  {
	  try {  
		  lock.enter();
		  
		  if ( connection == null ){
			  
			  Debug.out( "connection is null" );
			  
			  return;
		  }
		  
		  if ( handler_type == TransferProcessor.TYPE_UPLOAD ){
			  
			  SinglePeerUploader upload_entity = (SinglePeerUploader)upgraded_connections.remove( connection );  //remove from the upgraded list
			  
			  if( upload_entity != null ){
				  
				  net_man.removeWriteEntity( upload_entity );  //cancel from write processing
				  
			  }else{
				  
				  Debug.out( "upload_entity == null" );
			  }
			  
			  global_uploader.addPeerConnection( connection );  //move back to the general pool
			  
		  }else{
			  
			  SinglePeerDownloader download_entity = (SinglePeerDownloader)upgraded_connections.remove( connection );  //remove from the upgraded list
			  
			  if( download_entity != null ){
				  
				  net_man.removeReadEntity( download_entity );  //cancel from read processing
				  
			  }else{
				  
				  Debug.out( "download_entity == null" );
			  }
			  
			  global_downloader.addPeerConnection( connection );  //move back to the general pool
		  }
	  }finally{
		  
		  lock.exit();  
	  }
  }

  public RateHandler
  getRateHandler(
	 NetworkConnectionBase		connection )
  {
	  try{
		  lock.enter();

		  if ( connection == null ){
			  
			  Debug.out( "connection is null" );
			  
			  return( null );
		  }
		  
		  if( handler_type == TransferProcessor.TYPE_UPLOAD ){

			  SinglePeerUploader upload_entity = (SinglePeerUploader)upgraded_connections.get( connection );

			  if ( upload_entity != null ){

				  return( upload_entity.getRateHandler());
			  }else{

				  return( global_uploader.getRateHandler());
			  }
		  }else{

			  SinglePeerDownloader download_entity = (SinglePeerDownloader)upgraded_connections.get( connection );

			  if ( download_entity != null ){

				  return( download_entity.getRateHandler());
				  
			  }else{

				  return( global_downloader.getRateHandler());
			  }
		  }
	  }finally{
		  
		  lock.exit();
	  }
  }

  /**
   * Is the general pool entity in need of a transfer op.
   * NOTE: Because the general pool is backed by a MultiPeer entity,
   * it requires at least MSS available bytes before it will/can perform
   * a successful transfer.  This method allows higher-level bandwidth allocation to
   * determine if it should reserve the necessary MSS bytes for the general pool's needs.
   * @return true of it has data to transfer, false if not
   */
  /*
  public boolean isGeneralPoolReserveNeeded() {
    if( handler_type == TransferProcessor.TYPE_UPLOAD ) {
      return global_uploader.hasWriteDataAvailable();
    }
    return global_downloader.hasReadDataAvailable();
  }
  */

}
