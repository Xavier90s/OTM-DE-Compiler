/*
 * Copyright (c) 2012, Sabre Corporation and affiliates.
 * All Rights Reserved.
 * Use is subject to license agreement.
 */
package com.sabre.schemacompiler.security;


/**
 * Handles the verification of user ID and password credentials submitted by a web service
 * client request.
 * 
 * @author S. Livezey
 */
public interface AuthenticationProvider {
	
	/**
	 * Returns true if the given user is an authorized user of this repository and the password
	 * is verified as being valid.
	 * 
	 * @param userId  the user ID to verify
	 * @param password  the user's password to be authenticated
	 * @return boolean
	 * @throws RepositorySecurityException  thrown if an error occurs during the authentication process
	 */
	public boolean isValidUser(String userId, String password) throws RepositorySecurityException;
	
}