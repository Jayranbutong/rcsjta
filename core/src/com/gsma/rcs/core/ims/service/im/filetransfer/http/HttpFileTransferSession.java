/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.core.ims.service.im.filetransfer.http;

import com.gsma.rcs.core.content.MmContent;
import com.gsma.rcs.core.ims.network.NetworkException;
import com.gsma.rcs.core.ims.protocol.PayloadException;
import com.gsma.rcs.core.ims.protocol.sip.SipRequest;
import com.gsma.rcs.core.ims.service.ImsServiceError;
import com.gsma.rcs.core.ims.service.ImsSessionListener;
import com.gsma.rcs.core.ims.service.im.InstantMessagingService;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileSharingError;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileSharingSession;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileSharingSessionListener;
import com.gsma.rcs.provider.contact.ContactManager;
import com.gsma.rcs.provider.messaging.MessagingLog;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.provider.settings.RcsSettingsData.FileTransferProtocol;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.contact.ContactId;

import android.net.Uri;

/**
 * Abstract file transfer HTTP session
 * 
 * @author jexa7410
 */
public abstract class HttpFileTransferSession extends FileSharingSession implements
        HttpTransferEventListener {

    /**
     * HttpFileTransferSession state
     */
    public enum State {

        /**
         * Session is pending (not yet accepted by a final response by the remote)
         */
        PENDING,

        /**
         * Session has been established (i.e. 200 OK/ACK exchanged)
         */
        ESTABLISHED;
    }

    private State mSessionState;

    protected long mFileExpiration;

    protected long mIconExpiration;

    protected final MessagingLog mMessagingLog;

    private static final Logger sLogger = Logger.getLogger(HttpFileTransferSession.class
            .getSimpleName());

    /**
     * Constructor
     * 
     * @param imService InstantMessagingService
     * @param content Content to share
     * @param contact Remote contact identifier
     * @param remoteContact the remote contact URI
     * @param fileIcon Content of file icon
     * @param chatContributionId Chat contribution Id
     * @param fileTransferId File transfer Id
     * @param rcsSettings
     * @param messagingLog
     * @param timestamp Local timestamp for the session
     * @param fileExpiration
     * @param iconExpiration
     * @param contactManager
     */
    public HttpFileTransferSession(InstantMessagingService imService, MmContent content,
            ContactId contact, Uri remoteContact, MmContent fileIcon, String chatContributionId,
            String fileTransferId, RcsSettings rcsSettings, MessagingLog messagingLog,
            long timestamp, long fileExpiration, long iconExpiration, ContactManager contactManager) {
        super(imService, content, contact, remoteContact, fileIcon, fileTransferId, rcsSettings,
                timestamp, contactManager);

        setContributionID(chatContributionId);
        mSessionState = State.PENDING;
        mFileExpiration = fileExpiration;
        mIconExpiration = iconExpiration;
        mMessagingLog = messagingLog;
    }

    @Override
    public boolean isHttpTransfer() {
        return true;
    }

    /**
     * Returns the time when file on the content server is no longer valid to download.
     * 
     * @return the time
     */
    public long getFileExpiration() {
        return mFileExpiration;
    }

    /**
     * Returns the time when file icon on the content server is no longer valid to download.
     * 
     * @return the time
     */
    public long getIconExpiration() {
        return mIconExpiration;
    }

    /**
     * Create an INVITE request
     * 
     * @return the INVITE request
     * @throws PayloadException
     */
    public SipRequest createInvite() throws PayloadException {
        // Not used here
        return null;
    }

    protected void closeHttpSession(TerminationReason reason) throws PayloadException,
            NetworkException {
        interruptSession();
        closeSession(reason);
        removeSession();
    }

    @Override
    public void handleError(ImsServiceError error) {
        if (isSessionInterrupted()) {
            return;
        }
        if (sLogger.isActivated()) {
            sLogger.info(new StringBuilder("Transfer error: ").append(error.getErrorCode())
                    .append(", reason=").append(error.getMessage()).toString());
        }
        removeSession();
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : getListeners()) {
            ((FileSharingSessionListener) listener).onTransferError(new FileSharingError(error),
                    contact);
        }
    }

    @Override
    public void prepareMediaSession() {
        /* Not used here */
    }

    @Override
    public void openMediaSession() {
        /* Not used here */
    }

    @Override
    public void startMediaTransfer() {
        /* Not used here */
    }

    @Override
    public void closeMediaSession() {
        /* Not used here */
    }

    /**
     * Handle file transfered. In case of file transfer over MSRP, the terminating side has received
     * the file, but in case of file transfer over HTTP, only the content server has received the
     * file.
     */
    public void handleFileTransferred() {
        fileTransfered();
        removeSession();

        ContactId contact = getRemoteContact();
        MmContent content = getContent();
        long fileExpiration = getFileExpiration();
        long fileIconExpiration = getIconExpiration();
        for (ImsSessionListener listener : getListeners()) {
            ((FileSharingSessionListener) listener).onFileTransfered(content, contact,
                    fileExpiration, fileIconExpiration, FileTransferProtocol.HTTP);
        }
    }

    @Override
    public void onHttpTransferProgress(long currentSize, long totalSize) {
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : getListeners()) {
            ((FileSharingSessionListener) listener).onTransferProgress(contact, currentSize,
                    totalSize);
        }
    }

    @Override
    public void onHttpTransferNotAllowedToSend() {
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : getListeners()) {
            ((FileSharingSessionListener) listener).onTransferNotAllowedToSend(contact);
        }
    }

    @Override
    public void onHttpTransferStarted() {
        mSessionState = State.ESTABLISHED;
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : getListeners()) {
            ((FileSharingSessionListener) listener).onSessionStarted(contact);
        }
    }

    @Override
    public void onHttpTransferPausedByUser() {
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : getListeners()) {
            ((FileSharingSessionListener) listener).onFileTransferPausedByUser(contact);
        }
    }

    @Override
    public void onHttpTransferPausedBySystem() {
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : getListeners()) {
            ((FileSharingSessionListener) listener).onFileTransferPausedBySystem(contact);
        }
    }

    @Override
    public void onHttpTransferResumed() {
        ContactId contact = getRemoteContact();
        for (ImsSessionListener listener : getListeners()) {
            ((FileSharingSessionListener) listener).onFileTransferResumed(contact);
        }
    }

    /**
     * Get session state
     * 
     * @return State
     */
    public State getSessionState() {
        return mSessionState;
    }

    /**
     * Pausing file transfer Implementation should be overridden in subclasses
     */
    public abstract void onPause();

    /**
     * Resuming file transfer Implementation should be overridden in subclasses
     */
    public abstract void onResume();

    @Override
    public void receiveBye(SipRequest bye) throws PayloadException, NetworkException {
        super.receiveBye(bye);
        ContactId remote = getRemoteContact();
        for (ImsSessionListener listener : getListeners()) {
            listener.onSessionAborted(remote, TerminationReason.TERMINATION_BY_REMOTE);
        }
        getImsService().getImsModule().getCapabilityService().requestContactCapabilities(remote);
    }
}
