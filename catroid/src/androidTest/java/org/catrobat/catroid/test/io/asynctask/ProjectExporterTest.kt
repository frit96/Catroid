/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2021 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.test.io.asynctask

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.catrobat.catroid.ProjectManager
import org.catrobat.catroid.common.Constants
import org.catrobat.catroid.content.Project
import org.catrobat.catroid.io.StorageOperations
import org.catrobat.catroid.io.asynctask.ProjectExporter
import org.catrobat.catroid.io.asynctask.saveProjectSerial
import org.catrobat.catroid.utils.notifications.StatusBarNotificationManager
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.io.File
import java.io.IOException
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ProjectExporterTest {
    @get:Rule
    var runtimePermissionRule = GrantPermissionRule.grant(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private lateinit var project: Project
    private lateinit var contextMock: Context

    @Before
    fun setUp() {
        project = Project(
            ApplicationProvider.getApplicationContext(),
            ProjectExporterTest::class.java.simpleName
        )
        ProjectManager.getInstance().currentProject = project
        saveProjectSerial(project, ApplicationProvider.getApplicationContext())
        val notificationManagerMock = Mockito.mock(NotificationManager::class.java)
        contextMock = Mockito.mock(Context::class.java)
        Mockito.`when`(contextMock.resources)
            .thenReturn(ApplicationProvider.getApplicationContext<Context>().resources)
        Mockito.`when`(contextMock.getSystemService(ArgumentMatchers.anyString()))
            .thenReturn(notificationManagerMock)
    }

    @Test
    fun exportProjectTest() {
        createUndoCodeXmlFile()
        val notificationManager = StatusBarNotificationManager(contextMock)
        val notificationData = notificationManager.createSaveProjectToExternalMemoryNotification(
            ApplicationProvider.getApplicationContext(),
            project.name
        )
        ProjectExporter(
            project.directory,
            notificationData,
            ApplicationProvider.getApplicationContext()
        ).exportProjectToExternalStorage()
        val externalProjectZip = File(
            Constants.EXTERNAL_STORAGE_ROOT_EXPORT_DIRECTORY,
            project.directory.name + Constants.CATROBAT_EXTENSION
        )
        Assert.assertTrue(externalProjectZip.exists())
        checkUndoCodeXmlFileIsDeleted(externalProjectZip)
    }

    private fun createUndoCodeXmlFile() {
        val currentCodeFile = File(project.directory, Constants.CODE_XML_FILE_NAME)
        val undoCodeFile = File(project.directory, Constants.UNDO_CODE_XML_FILE_NAME)
        try {
            StorageOperations.transferData(currentCodeFile, undoCodeFile)
        } catch (exception: IOException) {
            Log.e(TAG, "Copying project ${project.name} failed.", exception)
        }
    }

    private fun checkUndoCodeXmlFileIsDeleted(externalProjectZip: File) {
        val zipFileName =
            externalProjectZip.absolutePath.replace(Constants.CATROBAT_EXTENSION, ".zip")
        externalProjectZip.renameTo(File(zipFileName))
        try {
            val zipFile = ZipFile(zipFileName)
            val zipEntries: Enumeration<*> = zipFile.entries()
            var fileName: String
            while (zipEntries.hasMoreElements()) {
                fileName = (zipEntries.nextElement() as ZipEntry).name
                Assert.assertFalse(fileName == Constants.UNDO_CODE_XML_FILE_NAME)
            }
        } catch (exception: IOException) {
            Log.e(TAG, "Creating zip folder failed.", exception)
        }
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        if (project.directory.isDirectory) {
            StorageOperations.deleteDir(project.directory)
        }
        if (Constants.EXTERNAL_STORAGE_ROOT_EXPORT_DIRECTORY.exists()) {
            StorageOperations.deleteDir(Constants.EXTERNAL_STORAGE_ROOT_EXPORT_DIRECTORY)
        }
    }

    companion object {
        val TAG = ProjectExporterTest::class.java.simpleName
    }
}
