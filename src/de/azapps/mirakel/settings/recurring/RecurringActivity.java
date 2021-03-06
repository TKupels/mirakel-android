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
package de.azapps.mirakel.settings.recurring;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import de.azapps.mirakel.model.recurring.Recurring;
import de.azapps.mirakel.settings.ListSettings;
import de.azapps.mirakelandroid.R;

public class RecurringActivity extends ListSettings {

	private Recurring recurring;
	@SuppressLint("NewApi")
	@Override
	protected OnClickListener getAddOnClickListener() {
		return new OnClickListener() {

			@Override
			public void onClick(View v) {
				newRecurring();
				clickOnLast();
				invalidateHeaders();
			}
		};
	}

	protected Recurring newRecurring() {
		return Recurring.newRecurring(getString(R.string.new_recurring), 0, 0, 0, 0, 1, true,null,null);
		
	}

	@Override
	protected int getSettingsRessource() {
		return R.xml.settings_recurring;
	}

	@Override
	protected void setupSettings() {
		recurring = Recurring.get(getIntent().getIntExtra("id", 0));
		new RecurringSettings(this, recurring).setup();
	}

	@Override
	protected List<Pair<Integer, String>> getItems() {
		List<Recurring> recurring = Recurring.all();
		List<Pair<Integer, String>> items = new ArrayList<Pair<Integer, String>>();
		for (Recurring r : recurring) {
			items.add(new Pair<Integer, String>(r.getId(), r.getLabel()));
		}
		return items;
	}

	@Override
	protected Class<?> getDestClass() {
		return RecurringActivity.class;
	}

	@Override
	protected Class<?> getDestFragmentClass() {
		return RecurringFragment.class;
	}

	@Override
	protected int getTitleRessource() {
		return R.string.recurring;
	}

	@Override
	protected OnClickListener getHelpOnClickListener() {
		// TODO Auto-generated method stub
		return null;
	}

}
