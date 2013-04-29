/*******************************************************************************
 * Mirakel is an Android App for managing your ToDo-Lists
 * 
 * Copyright (c) 2013 Anatolij Zelenin, Georg Semmler.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Modified by weiznich 2013
 */
package de.azapps.mirakel.sync;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import org.apache.http.message.BasicNameValuePair;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import com.google.gson.Gson;

import de.azapps.mirakel.Mirakel;
import de.azapps.mirakel.Pair;
import de.azapps.mirakel.R;
import de.azapps.mirakel.model.List_mirakle;
import de.azapps.mirakel.model.ListsDataSource;
import de.azapps.mirakel.model.Task;
import de.azapps.mirakel.model.TasksDataSource;

public class SyncAdapter extends AbstractThreadedSyncAdapter {

	private static final String TAG = "SyncAdapter";

	private final AccountManager mAccountManager;
	private String Email;
	private String Password;
	private String ServerUrl;

	private List<Pair<Network, String>> DeleteLists;
	private List<Pair<Network, String>> DeleteTasks;
	private List<Pair<Network, String>> AddLists;
	private List<Pair<Network, String>> AddTasks;
	private List<Pair<Network, String>> SyncLists;
	private List<Pair<Network, String>> SyncTasks;
	
	private List_mirakle[] ServerLists;
	private List<Task> ServerTasks;

	private boolean finish_list;
	private boolean finish_task;
	private int listAdd;

	private ListsDataSource listDataSource;
	private TasksDataSource taskDataSource;

	private final Context mContext;

	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		mContext = context;
		mAccountManager = AccountManager.get(context);
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority,
			ContentProviderClient provider, SyncResult syncResult) {
		taskDataSource = new TasksDataSource(mContext);
		listDataSource = new ListsDataSource(mContext);
		taskDataSource.open();
		listDataSource.open();
		DeleteLists = new ArrayList<Pair<Network, String>>();
		DeleteTasks = new ArrayList<Pair<Network, String>>();
		AddLists = new ArrayList<Pair<Network, String>>();
		AddTasks = new ArrayList<Pair<Network, String>>();
		SyncLists = new ArrayList<Pair<Network, String>>();
		SyncTasks = new ArrayList<Pair<Network, String>>();
		finish_list = false;
		finish_task = false;
		listAdd=0;

		// TODO close datasouces, by where???
		Log.v(TAG, "Syncing");

		Email = account.name;
		Password = mAccountManager.getPassword(account);
		ServerUrl = mAccountManager.getUserData(account,
				Mirakel.BUNDLE_SERVER_URL);

		// Remove Lists server
		List<List_mirakle> deletedLists = listDataSource
				.getListsBySyncState(Mirakel.SYNC_STATE_DELETE);
		if (deletedLists != null) {
			for (List_mirakle deletedList : deletedLists) {
				delete_list(deletedList);
			}
		}

		// Remove Tasks from server
		List<Task> deletedTasks = taskDataSource
				.getTasksBySyncState(Mirakel.SYNC_STATE_DELETE);
		if (deletedTasks != null) {
			for (Task deletedTask : deletedTasks) {
				delete_task(deletedTask);
			}
		}

		// get Server-Tasklist
		new Network(new DataDownloadCommand() {
			@Override
			public void after_exec(String result) {
				ServerTasks = taskDataSource.parse_json(result);
				//merge_with_server(tasks_server);

				// ContentValues values = new ContentValues();
				// values.put("sync_state", Mirakel.SYNC_STATE_NOTHING);
				// Mirakel.getWritableDatabase().update(Mirakel.TABLE_TASKS,
				// values, "not sync_state="
				// + Mirakel.SYNC_STATE_DELETE, null);
				finish_task = true;
				doSync();
			}
		}, Email, Password, Mirakel.Http_Mode.GET).execute(ServerUrl
				+ "/lists/all/tasks.json");

		// get Server List-List
		new Network(new DataDownloadCommand() {
			@Override
			public void after_exec(String result) {
				ServerLists = new Gson().fromJson(result,
						List_mirakle[].class);
				//merge_with_server(lists_server);
				List<List_mirakle> lists_local = listDataSource.getListsBySyncState(Mirakel.SYNC_STATE_ADD);
				for (List_mirakle list : lists_local) {
					add_list(list);
				}
				// ContentValues values = new ContentValues();
				// values.put("sync_state", Mirakel.SYNC_STATE_NOTHING);
				// Mirakel.getWritableDatabase().update(Mirakel.TABLE_LISTS,
				// values, "not sync_state="+ Mirakel.SYNC_STATE_DELETE, null);
				finish_list = true;
				doSync();

			}
		}, Email, Password, Mirakel.Http_Mode.GET).execute(ServerUrl
				+ "/lists.json");
	}

	protected void doSync() {
		if (finish_list && finish_task) {
			merge_with_server(ServerLists);
			merge_with_server(ServerTasks);
			Log.d(TAG, "Execute Sync");
			execute(DeleteTasks);
			execute(DeleteLists);
			execute(SyncLists);
			execute(SyncTasks);
			execute(AddLists);
		} else {
			Log.e(TAG, "Waiting for other");
		}
	}
	
	private void addTasks() {
		listAdd++;
		if(listAdd==AddLists.size()){
			List<Task> tasks_local = taskDataSource.getTasksBySyncState(Mirakel.SYNC_STATE_ADD);
			for (int i = 0; i < tasks_local.size(); i++) {
				add_task(tasks_local.get(i));
			}
			execute(AddTasks);
		}
	}

	private void execute(List<Pair<Network, String>> CommandList) {
		for (Pair<Network, String> command : CommandList) {
			command.getLeft().execute(command.getRight());
		}
	}

	// Own Functions
	// TODO NEED TO CLEANUP

	/**
	 * Delete a List from the Server
	 * 
	 * @param list
	 */
	protected void delete_list(final List_mirakle list) {
		DeleteLists.add(new Pair<Network, String>(new Network(
				new DataDownloadCommand() {
					@Override
					public void after_exec(String result) {
						list.setSync_state(Mirakel.SYNC_STATE_ADD);
						listDataSource.deleteList(list);
					}
				}, Email, Password, Mirakel.Http_Mode.DELETE), ServerUrl
				+ "/lists/" + list.getId() + ".json"));

	}

	/**
	 * Sync one List with the Server
	 * 
	 * @param list
	 */
	public void sync_list(List_mirakle list) {
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("list[name]", list.getName()));
		list.setSync_state(Mirakel.SYNC_STATE_IS_SYNCED);
		listDataSource.saveList(list);
		SyncLists.add(new Pair<Network, String>(new Network(
				new DataDownloadCommand() {
					@Override
					public void after_exec(String result) {
						// Do Nothing
					}
				}, Email, Password, Mirakel.Http_Mode.PUT, data), ServerUrl
				+ "/lists/" + list.getId() + ".json"));
	}

	/**
	 * Create a List on the Server
	 * 
	 * @param list
	 */
	public void add_list(final List_mirakle list) {
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("list[name]", list.getName()));
		AddLists.add(new Pair<Network, String>(new Network(
				new DataDownloadCommand() {
					@Override
					public void after_exec(String result) {
						List_mirakle list_response = new Gson().fromJson(
								result, List_mirakle.class);
						if (list.getId() < list_response.getId()) {
							long diff = list_response.getId() - list.getId();
							// Should be all lists with _id> list_id
							// TODO update ids in Tasks!!!
							Cursor c = Mirakel.getReadableDatabase().rawQuery(
									"Select _id from " + Mirakel.TABLE_LISTS
											+ " WHERE sync_state="
											+ Mirakel.SYNC_STATE_ADD
											+ " and _id>=" + list.getId(), null);
							c.moveToFirst();
							/*while (!c.isAfterLast()) {
								Mirakel.getWritableDatabase().execSQL(
										"UPDATE " + Mirakel.TABLE_TASKS
												+ " SET list_id=list_id+"
												+ diff + " WHERE list_id="
												+ c.getInt(0));
								c.moveToNext();
							}*/
							c.close();
							c= Mirakel.getReadableDatabase().rawQuery("Select _id from "+Mirakel.TABLE_LISTS+" WHERE sync_state="
											+ Mirakel.SYNC_STATE_ADD
											+ " and _id>" + list.getId(),null);
							c.moveToLast();
							while (!c.isBeforeFirst()) {
								Mirakel.getWritableDatabase().execSQL(
										"UPDATE " + Mirakel.TABLE_LISTS
												+ " SET _id=_id+" + diff
												+ " WHERE sync_state="
												+ Mirakel.SYNC_STATE_ADD
												+ " and _id=" + c.getInt(0));
								c.moveToPrevious();
							}
							c.close();

						}
						ContentValues values = new ContentValues();
						values.put("_id", list_response.getId());
						values.put("sync_state", Mirakel.SYNC_STATE_NOTHING);
						Mirakel.getWritableDatabase().update(
								Mirakel.TABLE_LISTS, values,
								"_id=" + list.getId(), null);
						values = new ContentValues();
						values.put("list_id", list_response.getId());
						Mirakel.getWritableDatabase().update(
								Mirakel.TABLE_TASKS, values,
								"list_id=" + list.getId(), null);
						addTasks();

					}
				}, Email, Password, Mirakel.Http_Mode.POST, data), ServerUrl
				+ "/lists.json"));
	}

	/**
	 * Merge Lists
	 * 
	 * @param lists_server
	 */
	protected void merge_with_server(List_mirakle[] lists_server) {
		for (List_mirakle list_server : lists_server) {
			List_mirakle list = listDataSource.getList(list_server.getId());
			if (list == null) {
				list_server.setSync_state(Mirakel.SYNC_STATE_IS_SYNCED);
				long id = Mirakel.getWritableDatabase().insert(
						Mirakel.TABLE_LISTS, null,
						list_server.getContentValues());
				ContentValues values = new ContentValues();
				values.put("_id", list_server.getId());
				values.put("sync_state", Mirakel.SYNC_STATE_IS_SYNCED);
				Mirakel.getWritableDatabase().update(Mirakel.TABLE_LISTS,
						values, "_id=" + id, null);
				continue;
			} else {
				if (list.getSync_state() == Mirakel.SYNC_STATE_NOTHING) {
					list_server.setSync_state(Mirakel.SYNC_STATE_IS_SYNCED);
					listDataSource.saveList(list_server);
				} else if (list.getSync_state() == Mirakel.SYNC_STATE_NEED_SYNC) {
					DateFormat df = new SimpleDateFormat(
							mContext.getString(R.string.dateTimeFormat),
							Locale.US);// use ASCII-Formating
					try {
						if (df.parse(list.getUpdated_at()).getTime() > df
								.parse(list_server.getUpdated_at()).getTime()) {
							// local list newer,
							sync_list(list);
						} else {
							// server list newer
							list_server
									.setSync_state(Mirakel.SYNC_STATE_IS_SYNCED);
							listDataSource.saveList(list_server);
						}
					} catch (ParseException e) {
						Log.e(TAG, "Unabel to parse Dates");
						e.printStackTrace();
					}
				}else if(list.getSync_state()==Mirakel.SYNC_STATE_ADD){
					Cursor c=Mirakel.getReadableDatabase().rawQuery("Select max(_id) from "+Mirakel.TABLE_LISTS+" where not sync_state="+Mirakel.SYNC_STATE_ADD,null);
					c.moveToFirst();
					if(c.getCount()!=0)
					{	
						int diff=c.getInt(0)-list.getId()<0?1:c.getInt(0)-list.getId();
						c.close();
						c= Mirakel.getReadableDatabase().rawQuery("Select _id from "+Mirakel.TABLE_LISTS+" WHERE sync_state="
									+ Mirakel.SYNC_STATE_ADD
									+ " and _id>=" + list.getId(),null);
						c.moveToLast();
						while (!c.isBeforeFirst()) {
							Mirakel.getWritableDatabase().execSQL(
									"UPDATE " + Mirakel.TABLE_LISTS
											+ " SET _id=_id+" + diff
											+ " WHERE sync_state="
											+ Mirakel.SYNC_STATE_ADD
											+ " and _id=" + c.getInt(0));
							c.moveToPrevious();
						}
						c.close();
						listDataSource.saveList(list_server);
					}
				}else {
					Log.wtf(TAG, "Syncronisation Error, Listmerge");
				}
			}
		}
		// Remove Tasks, which are deleted from server
		Mirakel.getWritableDatabase().execSQL(
				"Delete from " + Mirakel.TABLE_LISTS + " where sync_state="
						+ Mirakel.SYNC_STATE_NOTHING + " or sync_state="
						+ Mirakel.SYNC_STATE_NEED_SYNC);
		// Set Sync state to Nothing
		Mirakel.getWritableDatabase().execSQL(
				"Update " + Mirakel.TABLE_LISTS + " set sync_state="
						+ Mirakel.SYNC_STATE_NOTHING + " where sync_state="
						+ Mirakel.SYNC_STATE_IS_SYNCED);
	}

	// TASKS

	/**
	 * Delete a Task from the Server
	 * 
	 * @param task
	 *            , Task to Delete
	 */
	protected void delete_task(final Task task) {
		DeleteTasks.add(new Pair<Network, String>(new Network(
				new DataDownloadCommand() {
					@Override
					public void after_exec(String result) {
						// Remove Task
						task.setSync_state(Mirakel.SYNC_STATE_ADD);
						taskDataSource.deleteTask(task);
					}
				}, Email, Password, Mirakel.Http_Mode.DELETE), ServerUrl
				+ "/lists/" + task.getListId() + "/tasks/" + task.getId()
				+ ".json"));

	}

	/**
	 * Sync a Task with the server
	 * 
	 * @param task
	 *            , Task to sync
	 */
	protected void sync_task(Task task) {
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("task[name]", task.getName()));
		data.add(new BasicNameValuePair("task[priority]", task.getPriority()
				+ ""));
		data.add(new BasicNameValuePair("task[done]", task.isDone() + ""));
		GregorianCalendar due = task.getDue();
		String dueString = due.compareTo(new GregorianCalendar(1970, 1, 10)) < 0 ? "null"
				: new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
						.format(due.getTime());
		data.add(new BasicNameValuePair("task[due]", dueString));
		data.add(new BasicNameValuePair("task[content]", task.getContent()));
		task.setSync_state(Mirakel.SYNC_STATE_IS_SYNCED);
		taskDataSource.saveTask(task);
		SyncTasks.add(new Pair<Network, String>(new Network(
				new DataDownloadCommand() {
					@Override
					public void after_exec(String result) {
						// Do Nothing
					}
				}, Email, Password, Mirakel.Http_Mode.PUT, data), ServerUrl
				+ "/lists/" + task.getListId() + "/tasks/" + task.getId()
				+ ".json"));
	}

	/**
	 * Create a Task on the Server
	 * 
	 * @param task
	 *            , Task to add
	 */
	protected void add_task(final Task task) {
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("task[name]", task.getName()));
		data.add(new BasicNameValuePair("task[priority]", task.getPriority()
				+ ""));
		data.add(new BasicNameValuePair("task[done]", task.isDone() + ""));
		GregorianCalendar due = task.getDue();
		data.add(new BasicNameValuePair("task[due]", due
				.before(new GregorianCalendar(1970, 2, 2)) ? "null"
				: (new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
						.format(due.getTime()))));
		data.add(new BasicNameValuePair("task[content]", task.getContent()));
		// List must exists before syncing task!!
		List_mirakle list=listDataSource.getList((int)task.getListId());
		// Log.e(TAG,"Listid: "+list.getId()+"  Sync-State: "+list.getSync_state());
		//TODO Make better
		//
		
		AddTasks.add(new Pair<Network, String>(new Network(
				new DataDownloadCommand() {
					@Override
					public void after_exec(String result) {
						try {
							Task taskNew = taskDataSource.parse_json(
									"[" + result + "]").get(0);
							if (taskNew.getId() > task.getId()) {
								// Prevent id-Collision
								long diff = taskNew.getId() - task.getId();
								
								Cursor c= Mirakel.getWritableDatabase().rawQuery("Select _id from "+Mirakel.TABLE_TASKS+" WHERE sync_state="
										+ Mirakel.SYNC_STATE_ADD
										+ " and _id>" + task.getId(),null);
								c.moveToLast();
								while (!c.isBeforeFirst()) {
									Mirakel.getWritableDatabase().execSQL(
									"UPDATE " + Mirakel.TABLE_TASKS
											+ " SET _id=_id+" + diff
											+ " WHERE sync_state="
											+ Mirakel.SYNC_STATE_ADD
											+ " and _id=" + c.getInt(0));
									c.moveToPrevious();
								}
								c.close();
								/*Mirakel.getWritableDatabase().execSQL(
										"UPDATE " + Mirakel.TABLE_TASKS
												+ " SET _id=_id+" + diff
												+ " WHERE sync_state="
												+ Mirakel.SYNC_STATE_ADD
												+ "and _id>" + task.getId());*/
							}
							ContentValues values = new ContentValues();
							values.put("_id", taskNew.getId());
							values.put("sync_state", Mirakel.SYNC_STATE_NOTHING);
							Mirakel.getWritableDatabase().update(
									Mirakel.TABLE_TASKS, values,
									"_id=" + taskNew.getId(), null);
						} catch (IndexOutOfBoundsException e) {
							Log.e(TAG, "unknown Respons");
						}
					}
				}, Email, Password, Mirakel.Http_Mode.POST, data), ServerUrl
				+ "/lists/" + task.getListId() + "/tasks.json"));

	}

	/**
	 * Merge a Task with the Server
	 * 
	 * @param tasks_server
	 *            List<Task> with Tasks from server
	 */
	protected void merge_with_server(List<Task> tasks_server) {
		if (tasks_server == null)
			return;
		//TODO FIX Delete Tasks
		for (Task task_server : tasks_server) {
			Task task = taskDataSource.getTaskToSync(task_server.getId());
			if (task == null) {
				// New Task from server, add to db
				task_server.setSync_state(Mirakel.SYNC_STATE_IS_SYNCED);
				long id = Mirakel.getWritableDatabase().insert(
						Mirakel.TABLE_TASKS, null,
						task_server.getContentValues());
				if (id > task_server.getId()) {
					long diff = id - task_server.getId();
					Mirakel.getWritableDatabase().execSQL(
							"UPDATE " + Mirakel.TABLE_TASKS + " SET _id=_id+"
									+ diff + " WHERE sync_state="
									+ Mirakel.SYNC_STATE_ADD + "and _id>"
									+ task_server.getId());
				}
				ContentValues values = new ContentValues();
				values.put("_id", task_server.getId());
				Mirakel.getWritableDatabase().update(Mirakel.TABLE_TASKS,
						values, "_id=" + id, null);
				continue;
			} else {
				if (task.getSync_state() == Mirakel.SYNC_STATE_NOTHING) {
					task_server.setSync_state(Mirakel.SYNC_STATE_IS_SYNCED);
					taskDataSource.saveTask(task_server);
				} else if (task.getSync_state() == Mirakel.SYNC_STATE_NEED_SYNC) {
					DateFormat df = new SimpleDateFormat(
							mContext.getString(R.string.dateTimeFormat),
							Locale.US);// use ASCII-Formating
					try {
						if (df.parse(task.getUpdated_at()).getTime() > df
								.parse(task_server.getUpdated_at()).getTime()) {
							// local task newer, push to server
							sync_task(task);
						} else {
							// server task newer, use this task instated local
							task_server
									.setSync_state(Mirakel.SYNC_STATE_IS_SYNCED);
							taskDataSource.saveTask(task_server);
						}
					} catch (ParseException e) {
						Log.e(TAG, "Unabel to parse Dates");
						e.printStackTrace();
					}
				} else if(task.getSync_state()==Mirakel.SYNC_STATE_ADD){
					Cursor c=Mirakel.getReadableDatabase().rawQuery("Select max(_id) from "+Mirakel.TABLE_TASKS+" where not sync_state="+Mirakel.SYNC_STATE_ADD,null);
					c.moveToFirst();
					if(c.getCount()!=0)
					{	
						long diff=c.getInt(0)-task.getId()<0?1:c.getInt(0)-task.getId();
						c.close();
						c= Mirakel.getReadableDatabase().rawQuery("Select _id from "+Mirakel.TABLE_TASKS+" WHERE sync_state="
									+ Mirakel.SYNC_STATE_ADD
									+ " and _id>=" + task.getId(),null);
						c.moveToLast();
						while (!c.isBeforeFirst()) {
							Mirakel.getWritableDatabase().execSQL(
									"UPDATE " + Mirakel.TABLE_TASKS
											+ " SET _id=_id+" + diff
											+ " WHERE sync_state="
											+ Mirakel.SYNC_STATE_ADD
											+ " and _id=" + c.getInt(0));
							c.moveToPrevious();
						}
						c.close();
						taskDataSource.saveTask(task_server);
					}
				}else if(task.getSync_state()==Mirakel.SYNC_STATE_DELETE){
					//Nothing
				}else {
					Log.wtf(TAG, "Syncronisation Error, Taskmerge");
				}
			}
		}
		// Remove Tasks, which are deleted from server
		Mirakel.getWritableDatabase().execSQL(
				"Delete from " + Mirakel.TABLE_TASKS + " where sync_state="
						+ Mirakel.SYNC_STATE_NOTHING + " or sync_state="
						+ Mirakel.SYNC_STATE_NEED_SYNC);
		Mirakel.getWritableDatabase().execSQL(
				"Update " + Mirakel.TABLE_TASKS + " set sync_state="
						+ Mirakel.SYNC_STATE_NOTHING + " where sync_state="
						+ Mirakel.SYNC_STATE_IS_SYNCED);

	}

}
