/*******************************************************************************
 * Copyright 2014 Open Networking Laboratory
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package net.onrc.openvirtex.exceptions;

public class DuplicateIndexException extends Exception {

	private static final long serialVersionUID = 3725666959913773107L;

	public DuplicateIndexException() {
		super();
	}

	public DuplicateIndexException(final String msg) {
		super(msg);
	}

	public DuplicateIndexException(final Throwable msg) {
		super(msg);
	}
}
