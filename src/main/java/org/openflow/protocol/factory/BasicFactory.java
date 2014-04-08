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
/**
 *    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
 *    University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package org.openflow.protocol.factory;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.action.OFActionVendor;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.protocol.statistics.OFVendorStatistics;
import org.openflow.protocol.vendor.OFByteArrayVendorData;
import org.openflow.protocol.vendor.OFVendorData;
import org.openflow.protocol.vendor.OFVendorDataType;
import org.openflow.protocol.vendor.OFVendorId;

/**
 * A basic OpenFlow factory that supports naive creation of both Messages and
 * Actions.
 * 
 * @author David Erickson (daviderickson@cs.stanford.edu)
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 * 
 * @modified alshabib - return this to class
 */
public class BasicFactory implements OFMessageFactory, OFActionFactory,
		OFStatisticsFactory, OFVendorDataFactory {
	protected static BasicFactory instance = null;

	private final OFVendorActionRegistry vendorActionRegistry;

	protected BasicFactory() {
		this.vendorActionRegistry = OFVendorActionRegistry.getInstance();
	}

	public static BasicFactory getInstance() {
		if (BasicFactory.instance == null) {
			BasicFactory.instance = new BasicFactory();
		}
		return BasicFactory.instance;
	}

	/**
	 * create and return a new instance of a message for OFType t. Also injects
	 * factories for those message types that implement the *FactoryAware
	 * interfaces.
	 * 
	 * @return a newly created instance that may be modified / used freely by
	 *         the caller
	 */
	@Override
	public OFMessage getMessage(final OFType t) {
		final OFMessage message = t.newInstance();
		this.injectFactories(message);
		return message;
	}

	@Override
	public List<OFMessage> parseMessage(final ChannelBuffer data)
			throws MessageParseException {
		final List<OFMessage> msglist = new ArrayList<OFMessage>();
		OFMessage msg = null;

		while (data.readableBytes() >= OFMessage.MINIMUM_LENGTH) {
			data.markReaderIndex();
			msg = this.parseMessageOne(data);
			if (msg == null) {
				data.resetReaderIndex();
				break;
			} else {
				msglist.add(msg);
			}
		}

		if (msglist.size() == 0) {
			return null;
		}
		return msglist;

	}

	public OFMessage parseMessageOne(final ChannelBuffer data)
			throws MessageParseException {
		try {
			final OFMessage demux = new OFMessage();
			OFMessage ofm = null;

			if (data.readableBytes() < OFMessage.MINIMUM_LENGTH) {
				return ofm;
			}

			data.markReaderIndex();
			demux.readFrom(data);
			data.resetReaderIndex();

			if (demux.getLengthU() > data.readableBytes()) {
				return ofm;
			}

			ofm = this.getMessage(demux.getType());
			if (ofm == null) {
				return null;
			}

			this.injectFactories(ofm);
			ofm.readFrom(data);
			if (OFMessage.class.equals(ofm.getClass())) {
				// advance the position for un-implemented messages
				data.readerIndex(data.readerIndex() + ofm.getLengthU()
						- OFMessage.MINIMUM_LENGTH);
			}

			return ofm;
		} catch (final Exception e) {
			/* Write the offending data along with the error message */
			data.resetReaderIndex();
			final String msg = "Message Parse Error for packet:"
					+ BasicFactory.dumpBuffer(data) + "\nException: "
					+ e.toString();
			data.resetReaderIndex();

			throw new MessageParseException(msg, e);
		}
	}

	private void injectFactories(final OFMessage ofm) {
		if (ofm instanceof OFActionFactoryAware) {
			((OFActionFactoryAware) ofm).setActionFactory(this);
		}
		if (ofm instanceof OFMessageFactoryAware) {
			((OFMessageFactoryAware) ofm).setMessageFactory(this);
		}
		if (ofm instanceof OFStatisticsFactoryAware) {
			((OFStatisticsFactoryAware) ofm).setStatisticsFactory(this);
		}
		if (ofm instanceof OFVendorDataFactoryAware) {
			((OFVendorDataFactoryAware) ofm).setVendorDataFactory(this);
		}
	}

	@Override
	public OFAction getAction(final OFActionType t) {
		return t.newInstance();
	}

	@Override
	public List<OFAction> parseActions(final ChannelBuffer data,
			final int length) {
		return this.parseActions(data, length, 0);
	}

	@Override
	public List<OFAction> parseActions(final ChannelBuffer data,
			final int length, final int limit) {
		final List<OFAction> results = new ArrayList<OFAction>();
		final OFAction demux = new OFAction();
		OFAction ofa;
		final int end = data.readerIndex() + length;

		while (limit == 0 || results.size() <= limit) {
			if (data.readableBytes() < OFAction.MINIMUM_LENGTH
					|| data.readerIndex() + OFAction.MINIMUM_LENGTH > end) {
				return results;
			}

			data.markReaderIndex();
			demux.readFrom(data);
			data.resetReaderIndex();

			if (demux.getLengthU() > data.readableBytes()
					|| data.readerIndex() + demux.getLengthU() > end) {
				return results;
			}

			ofa = this.parseActionOne(demux.getType(), data);
			results.add(ofa);
		}

		return results;
	}

	private OFAction parseActionOne(final OFActionType type,
			final ChannelBuffer data) {
		OFAction ofa;
		data.markReaderIndex();
		ofa = this.getAction(type);
		ofa.readFrom(data);

		if (type == OFActionType.VENDOR) {
			final OFActionVendor vendorAction = (OFActionVendor) ofa;

			final OFVendorActionFactory vendorActionFactory = this.vendorActionRegistry
					.get(vendorAction.getVendor());

			if (vendorActionFactory != null) {
				// if we have a specific vendorActionFactory for this vendor id,
				// delegate to it for vendor-specific reparsing of the message
				data.resetReaderIndex();
				final OFActionVendor newAction = vendorActionFactory
						.readFrom(data);
				if (newAction != null) {
					ofa = newAction;
				}
			}
		}

		if (OFAction.class.equals(ofa.getClass())) {
			// advance the position for un-implemented messages
			data.readerIndex(data.readerIndex() + ofa.getLengthU()
					- OFAction.MINIMUM_LENGTH);
		}
		return ofa;
	}

	@Override
	public OFActionFactory getActionFactory() {
		return this;
	}

	@Override
	public OFStatistics getStatistics(final OFType t, final OFStatisticsType st) {
		return st.newInstance(t);
	}

	@Override
	public List<OFStatistics> parseStatistics(final OFType t,
			final OFStatisticsType st, final ChannelBuffer data,
			final int length) {
		return this.parseStatistics(t, st, data, length, 0);
	}

	/**
	 * @param t
	 *            OFMessage type: should be one of stats_request or stats_reply
	 * @param st
	 *            statistics type of this message, e.g., DESC, TABLE
	 * @param data
	 *            buffer to read from
	 * @param length
	 *            length of statistics
	 * @param limit
	 *            number of statistics to grab; 0 == all
	 * 
	 * @return list of statistics
	 */

	@Override
	public List<OFStatistics> parseStatistics(final OFType t,
			final OFStatisticsType st, final ChannelBuffer data,
			final int length, final int limit) {
		final List<OFStatistics> results = new ArrayList<OFStatistics>();
		OFStatistics statistics = this.getStatistics(t, st);

		final int start = data.readerIndex();
		int count = 0;

		while (limit == 0 || results.size() <= limit) {
			// TODO Create a separate MUX/DEMUX path for vendor stats
			if (statistics instanceof OFVendorStatistics) {
				((OFVendorStatistics) statistics).setLength(length);
			}

			/**
			 * can't use data.remaining() here, b/c there could be other data
			 * buffered past this message
			 */
			if (length - count >= statistics.getLength()) {
				if (statistics instanceof OFActionFactoryAware) {
					((OFActionFactoryAware) statistics).setActionFactory(this);
				}
				statistics.readFrom(data);
				results.add(statistics);
				count += statistics.getLength();
				statistics = this.getStatistics(t, st);
			} else {
				if (count < length) {
					/**
					 * Nasty case: partial/incomplete statistic found even
					 * though we have a full message. Found when NOX sent
					 * agg_stats request with wrong agg statistics length (52
					 * instead of 56)
					 * 
					 * just throw the rest away, or we will break framing
					 */
					data.readerIndex(start + length);
				}
				return results;
			}
		}
		return results; // empty; no statistics at all
	}

	@Override
	public OFVendorData getVendorData(final OFVendorId vendorId,
			final OFVendorDataType vendorDataType) {
		if (vendorDataType == null) {
			return null;
		}

		return vendorDataType.newInstance();
	}

	/**
	 * Attempts to parse and return the OFVendorData contained in the given
	 * ChannelBuffer, beginning right after the vendor id.
	 * 
	 * @param vendor
	 *            the vendor id that was parsed from the OFVendor message.
	 * @param data
	 *            the ChannelBuffer from which to parse the vendor data
	 * @param length
	 *            the length to the end of the enclosing message.
	 * @return an OFVendorData instance
	 */
	@Override
	public OFVendorData parseVendorData(final int vendor,
			final ChannelBuffer data, final int length) {
		OFVendorDataType vendorDataType = null;
		final OFVendorId vendorId = OFVendorId.lookupVendorId(vendor);
		if (vendorId != null) {
			data.markReaderIndex();
			vendorDataType = vendorId.parseVendorDataType(data, length);
			data.resetReaderIndex();
		}

		OFVendorData vendorData = this.getVendorData(vendorId, vendorDataType);
		if (vendorData == null) {
			vendorData = new OFByteArrayVendorData();
		}

		vendorData.readFrom(data, length);

		return vendorData;
	}

	public static String dumpBuffer(final ChannelBuffer data) {
		// NOTE: Reads all the bytes in buffer from current read offset.
		// Set/Reset ReaderIndex if you want to read from a different location
		final int len = data.readableBytes();
		final StringBuffer sb = new StringBuffer();
		for (int i = 0; i < len; i++) {
			if (i % 32 == 0) {
				sb.append("\n");
			}
			if (i % 4 == 0) {
				sb.append(" ");
			}
			sb.append(String.format("%02x", data.getUnsignedByte(i)));
		}
		return sb.toString();
	}

}
