package com.easyfitness.nourriture;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.easyfitness.AppViMo;
import com.easyfitness.BtnClickListener;
import com.easyfitness.DAO.Profile;
import com.easyfitness.DAO.macros.DAOFoodRecord;
import com.easyfitness.DAO.macros.FoodRecord;
import com.easyfitness.DatePickerDialogFragment;
import com.easyfitness.MainActivity;
import com.easyfitness.R;
import com.easyfitness.TimePickerDialogFragment;
import com.easyfitness.machines.MachineArrayFullAdapter;
import com.easyfitness.utils.DateConverter;
import com.easyfitness.utils.ExpandedListView;
import com.easyfitness.utils.Keyboard;
import com.ikovac.timepickerwithseconds.MyTimePickerDialog;
import com.onurkaganaldemir.ktoastlib.KToast;

import java.util.Calendar;
import java.util.Date;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class NourritureFragment extends Fragment {

    private int lTableColor = 1;
    private long mProgramId;
    private MainActivity mActivity = null;
    private AutoCompleteTextView foodNameEdit = null;
    private MachineArrayFullAdapter foodEditAdapter = null;
    private ImageButton foodListButton = null;
    private ImageButton detailsExpandArrow = null;
    private LinearLayout detailsLayout = null;
    private CardView detailsCardView = null;
    private CheckBox autoTimeCheckBox = null;
    private TextView dateEdit = null;

    private DAOFoodRecord mDbRecord = null;
    private AppViMo appViMo;
    private final DatePickerDialog.OnDateSetListener dateSet = (view, year, month, day) -> {
        dateEdit.setText(DateConverter.dateToLocalDateStr(year, month, day, getContext()));
        Keyboard.hide(getContext(), dateEdit);
    };
    private TextView timeEdit = null;
    private final MyTimePickerDialog.OnTimeSetListener timeSet = (view, hourOfDay, minute, second) -> {
        // Do something with the time chosen by the user
        Date date = DateConverter.timeToDate(hourOfDay, minute, second);
        timeEdit.setText(DateConverter.dateToLocalTimeStr(date, getContext()));
        Keyboard.hide(getContext(), timeEdit);
    };
    private final CompoundButton.OnCheckedChangeListener checkedAutoTimeCheckBox = (buttonView, isChecked) -> {
        dateEdit.setEnabled(!isChecked);
        timeEdit.setEnabled(!isChecked);
        if (isChecked) {
            dateEdit.setText(DateConverter.currentDate(getContext()));
            timeEdit.setText(DateConverter.currentTime(getContext()));
        }
    };
    private Button addButton = null;
    private ExpandedListView recordList = null;
    private AlertDialog foodListDialog;
    private AlertDialog foodFilterDialog;
    private DatePickerDialogFragment mDateFrag = null;
    private TimePickerDialogFragment mTimeFrag = null;
    private final OnClickListener clickDateEdit = v -> {
        int id = v.getId();
        if (id == R.id.editDate) {
            showDatePickerFragment();
        } else if (id == R.id.editTime) {
            showTimePicker(timeEdit);
        }
    };
    private final OnClickListener collapseDetailsClick = v -> {
        detailsLayout.setVisibility(detailsLayout.isShown() ? View.GONE : View.VISIBLE);
        detailsExpandArrow.setImageResource(detailsLayout.isShown() ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        saveSharedParams();
    };
    private final BtnClickListener itemClickCopyRecord = v -> {
        FoodRecord r = mDbRecord.getRecord((long) v.getTag());
        if (r == null) {
            return;
        }
        // Copy values above
        KToast.infoToast(getMainActivity(), getString(R.string.recordcopied), Gravity.BOTTOM, KToast.LENGTH_SHORT);
    };
    private final OnItemLongClickListener itemlongclickDeleteRecord = (listView, view, position, id) -> {
        showRecordListMenu(id);
        return true;
    };
    private final TextWatcher foodNameTextWatcher = new TextWatcher() {
        public void afterTextChanged(Editable s) {
            String foodName = s.toString();
            MachineArrayFullAdapter adapter = (MachineArrayFullAdapter) foodNameEdit.getAdapter();
            setCurrentFoodName(foodName);
        }

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    };

    private final OnClickListener clickAddButton = v -> {
        // Verifie que les infos sont completes
        if (foodNameEdit.getText().toString().isEmpty()) {
            KToast.warningToast(getActivity(), getResources().getText(R.string.missinginfo).toString(), Gravity.BOTTOM, KToast.LENGTH_SHORT);
            return;
        }

        Date date;

        if (autoTimeCheckBox.isChecked()) {
            date = new Date();
        } else {
            date = DateConverter.localDateTimeStrToDateTime(dateEdit.getText().toString(), timeEdit.getText().toString(), getContext());
        }

        getActivity().findViewById(R.id.drawer_layout).requestFocus();
        Keyboard.hide(getContext(), v);

        lTableColor = (lTableColor + 1) % 2; // Change la couleur a chaque ajout de donnees

        refreshData();

        saveSharedParams();
    };
    private final OnClickListener onClickFoodListWithIcons = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Cursor oldCursor;

            // In case the dialog is already open
            if (foodListDialog != null && foodListDialog.isShowing()) {
                return;
            }

//            ListView machineList = new ListView(v.getContext());
//
//            // Version avec table Machine
//            Cursor c = mDbMachine.getAllMachines(selectedTypes);
//
//            if (c == null || c.getCount() == 0) {
//                if (selectedTypes.size() == 0) {
//                    KToast.warningToast(getActivity(), getResources().getText(R.string.selectExerciseTypeFirst).toString(), Gravity.BOTTOM, KToast.LENGTH_SHORT);
//                } else {
//                    //Toast.makeText(getActivity(), R.string.createExerciseFirst, Toast.LENGTH_SHORT).show();
//                    KToast.warningToast(getActivity(), getResources().getText(R.string.createExerciseFirst).toString(), Gravity.BOTTOM, KToast.LENGTH_SHORT);
//                }
//                machineList.setAdapter(null);
//            } else {
//                if (machineList.getAdapter() == null) {
//                    MachineCursorAdapter mTableAdapter = new MachineCursorAdapter(getActivity(), c, 0, mDbMachine);
//                    //MachineArrayFullAdapter lAdapter = new MachineArrayFullAdapter(v.getContext(),records);
//                    machineList.setAdapter(mTableAdapter);
//                } else {
//                    MachineCursorAdapter mTableAdapter = (MachineCursorAdapter) machineList.getAdapter();
//                    oldCursor = mTableAdapter.swapCursor(c);
//                    if (oldCursor != null) oldCursor.close();
//                }
//
//                AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
//                View customLayout = getLayoutInflater().inflate(R.layout.tab_machine, null);
//                Button addButton = customLayout.findViewById(R.id.addExercise);
//                addButton.setVisibility(View.GONE);
//
//                AutoCompleteTextView textFilter = customLayout.findViewById(R.id.searchField);
//                textFilter.setVisibility(View.GONE);
//
//                TextView textViewFilterExplanation = customLayout.findViewById(R.id.textViewFilterByTypes);
//                textViewFilterExplanation.setVisibility(View.VISIBLE);
//
//                ImageButton filterButton = customLayout.findViewById(R.id.buttonFilterListMachine);
//                filterButton.setOnClickListener(clickFilterButton);
//                ListView listView = customLayout.findViewById(R.id.listMachine);
//                listView.setAdapter(machineList.getAdapter());
//                listView.setOnItemClickListener((parent, view, position, id) -> {
//                    TextView textView = view.findViewById(R.id.LIST_MACHINE_ID);
//                    long machineID = Long.parseLong(textView.getText().toString());
//                    DAOMachine lMachineDb = new DAOMachine(getContext());
//                    Machine lMachine = lMachineDb.getMachine(machineID);
//
//                    setCurrentMachine(lMachine.getName());
//
//                    getMainActivity().findViewById(R.id.drawer_layout).requestFocus();
//                    Keyboard.hide(getContext(), getMainActivity().findViewById(R.id.drawer_layout));
//
//                    if (foodListDialog.isShowing()) {
//                        foodListDialog.dismiss();
//                    }
//                });
//                builder.setTitle(R.string.selectMachineDialogLabel);
//                builder.setView(customLayout);
//                foodListDialog = builder.create();
//                foodListDialog.show();
//            }
        }
    };
    private final OnItemClickListener onItemClickFilterList = (parent, view, position, id) -> setCurrentFoodName(foodNameEdit.getText().toString());
    private final OnFocusChangeListener touchRazEdit = (v, hasFocus) -> {
        if (hasFocus) {


            v.post(() -> {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
            });
        } else {
            setCurrentFoodName(foodNameEdit.getText().toString());
        }
    };

    /**
     * Create a new instance of DetailsFragment, initialized to
     * show the text at 'index'.
     */
    public static NourritureFragment newInstance(int displayType, long templateId) {
        NourritureFragment f = new NourritureFragment();

        // Supply index input as an argument.
        Bundle args = new Bundle();
        args.putLong("templateId", templateId);
        args.putInt("displayType", displayType);
        f.setArguments(args);

        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.tab_nourriture, container, false);
        foodNameEdit = view.findViewById(R.id.editFood);
        recordList = view.findViewById(R.id.listRecord);
        foodListButton = view.findViewById(R.id.buttonListFoods);
        addButton = view.findViewById(R.id.addperff);

        detailsCardView = view.findViewById(R.id.detailsCardView);
        detailsLayout = view.findViewById(R.id.notesLayout);
        detailsExpandArrow = view.findViewById(R.id.buttonExpandArrow);

        autoTimeCheckBox = view.findViewById(R.id.autoTimeCheckBox);
        dateEdit = view.findViewById(R.id.editDate);
        timeEdit = view.findViewById(R.id.editTime);

        /* Initialisation des boutons */
        addButton.setOnClickListener(clickAddButton);
        foodListButton.setOnClickListener(onClickFoodListWithIcons);

        dateEdit.setOnClickListener(clickDateEdit);
        timeEdit.setOnClickListener(clickDateEdit);
        autoTimeCheckBox.setOnCheckedChangeListener(checkedAutoTimeCheckBox);

        foodNameEdit.addTextChangedListener(foodNameTextWatcher);
        foodNameEdit.setOnItemClickListener(onItemClickFilterList);
        recordList.setOnItemLongClickListener(itemlongclickDeleteRecord);
        detailsExpandArrow.setOnClickListener(collapseDetailsClick);

        restoreSharedParams();

        appViMo = new ViewModelProvider(requireActivity()).get(AppViMo.class);
        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        appViMo.getProfile().observe(getViewLifecycleOwner(), profile -> {
            // Update the UI, in this case, a TextView.
            refreshData();
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        this.mActivity = (MainActivity) this.getActivity();
        dateEdit.setText(DateConverter.currentDate(getContext()));
        timeEdit.setText(DateConverter.currentTime(getContext()));
        refreshData();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    // invoked when the activity may be temporarily destroyed, save the instance state here
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // call superclass to save any view hierarchy
        super.onSaveInstanceState(outState);
    }

    public String getName() {
        return getArguments().getString("name");
    }

    public MainActivity getMainActivity() {
        return (MainActivity) this.getActivity();
    }

    private void showRecordListMenu(final long id) {
        // Get the cursor, positioned to the corresponding row in the result set
        //Cursor cursor = (Cursor) listView.getItemAtPosition(position);

        String[] profilListArray = new String[2];
        profilListArray[0] = getActivity().getResources().getString(R.string.DeleteLabel);
        profilListArray[1] = getActivity().getResources().getString(R.string.EditLabel);

        AlertDialog.Builder itemActionbuilder = new AlertDialog.Builder(getView().getContext());
        itemActionbuilder.setTitle("").setItems(profilListArray, (dialog, which) -> {

            switch (which) {
                // Delete
                case 0:
                    showDeleteDialog(id);
                    break;
                // Edit
                case 1:
                    Toast.makeText(getActivity(), R.string.edit_soon_available, Toast.LENGTH_SHORT).show();
                    break;
                default:
            }
        });
        itemActionbuilder.show();
    }

    private void showDeleteDialog(final long idToDelete) {

        new SweetAlertDialog(getContext(), SweetAlertDialog.WARNING_TYPE)
                .setTitleText(getString(R.string.DeleteRecordDialog))
                .setContentText(getResources().getText(R.string.areyousure).toString())
                .setCancelText(getResources().getText(R.string.global_no).toString())
                .setConfirmText(getResources().getText(R.string.global_yes).toString())
                .showCancelButton(true)
                .setConfirmClickListener(sDialog -> {
                    mDbRecord.deleteRecord(idToDelete);

                    updateRecordTable(foodNameEdit.getText().toString());

                    // Info
                    KToast.infoToast(getActivity(), getResources().getText(R.string.removedid).toString(), Gravity.BOTTOM, KToast.LENGTH_LONG);
                    sDialog.dismissWithAnimation();
                })
                .show();
    }

    private void showDatePickerFragment() {
        if (mDateFrag == null) {
            mDateFrag = DatePickerDialogFragment.newInstance(dateSet);
            mDateFrag.show(getActivity().getSupportFragmentManager().beginTransaction(), "dialog");
        } else {
            if (!mDateFrag.isVisible())
                mDateFrag.show(getActivity().getSupportFragmentManager().beginTransaction(), "dialog");
        }
    }

    private void showTimePicker(TextView timeTextView) {
        Calendar calendar = Calendar.getInstance();
        Date time = DateConverter.localTimeStrToDate(timeTextView.getText().toString(), getContext());
        calendar.setTime(time);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int min = calendar.get(Calendar.MINUTE);
        int sec = calendar.get(Calendar.SECOND);

        if (timeTextView.getId() == R.id.editTime) {
            if (mTimeFrag == null) {
                mTimeFrag = TimePickerDialogFragment.newInstance(timeSet, hour, min, sec);
                mTimeFrag.show(getActivity().getSupportFragmentManager().beginTransaction(), "dialog_time");
            } else {
                if (!mTimeFrag.isVisible()) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("HOUR", hour);
                    bundle.putInt("MINUTE", min);
                    bundle.putInt("SECOND", sec);
                    mTimeFrag.setArguments(bundle);
                    mTimeFrag.show(getActivity().getSupportFragmentManager().beginTransaction(), "dialog_time");
                }
            }
        }
    }

    // Share your performances with friends
    public boolean shareRecord(String text) {
        AlertDialog.Builder newProfilBuilder = new AlertDialog.Builder(getView().getContext());

        newProfilBuilder.setTitle(getView().getContext().getResources().getText(R.string.ShareTitle));
        newProfilBuilder.setMessage(getView().getContext().getResources().getText(R.string.ShareInstruction));

        // Set an EditText view to get user input
        final EditText input = new EditText(getView().getContext());
        input.setText(text);
        newProfilBuilder.setView(input);

        newProfilBuilder.setPositiveButton(getView().getContext().getResources().getText(R.string.ShareText), (dialog, whichButton) -> {
            String value = input.getText().toString();

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, value);
            sendIntent.setType("text/plain");
            startActivity(sendIntent);
        });

        newProfilBuilder.setNegativeButton(getView().getContext().getResources().getText(android.R.string.cancel), (dialog, whichButton) -> {

        });

        newProfilBuilder.show();

        return true;
    }

    public NourritureFragment getFragment() {
        return this;
    }

    private Profile getProfile() {
        return appViMo.getProfile().getValue();
    }

    public String getFoodName() {
        return foodNameEdit.getText().toString();
    }

    private void setCurrentFoodName(String foodStr) {
        if (foodStr.isEmpty()) {
            return;
        }

        FoodRecord lFood = mDbRecord.getMostRecentFoodRecord(getProfile(), foodStr);
        if (lFood == null) {
            foodNameEdit.setText("");
            return;
        }

        // Update EditView
        if (!foodNameEdit.getText().toString().equals(lFood.getFoodName()))
            foodNameEdit.setText(lFood.getFoodName());

        // Update Table
        updateRecordTable(lFood.getFoodName());
        // Update last values
        updateLastRecord(lFood);
    }

    private void updateLastRecord(FoodRecord food) {
        /// TODO: Implement this
        if (food == null) {
            // Set default values or nothing.
        } else {
            // Fill in the inputs to match `food`'s value
        }
    }

    private void updateRecordTable(String pMachine) {
        // Informe l'activité de la machine courante
        this.getMainActivity().setCurrentMachine(pMachine);
        if (getView() == null) return;
        getView().post(() -> {

//            Cursor c = null;
//            if (mDisplayType == DisplayType.FREE_WORKOUT_DISPLAY) {
//                c = mDbRecord.getTop3DatesFreeWorkoutRecords(getProfile());
//            } else if (mDisplayType == DisplayType.PROGRAM_EDIT_DISPLAY) {
//                c = mDbRecord.getProgramTemplateRecords(mProgramId);
//            }
//
//            List<Record> records = mDbRecord.fromCursorToList(c);
//
//            if (records.isEmpty()) {
//                recordList.setAdapter(null);
//            } else {
//                if (recordList.getAdapter() == null) {
//                    RecordArrayAdapter mTableAdapter = new RecordArrayAdapter(getActivity(), getContext(), records, mDisplayType, itemClickCopyRecord);
//                    //RecordArrayAdapter mTableAdapter = new RecordArrayAdapter(getActivity(), getContext(), records, DisplayType.PROGRAM_EDIT_DISPLAY, itemClickCopyRecord);
//                    recordList.setAdapter(mTableAdapter);
//                } else {
//                    ((RecordArrayAdapter) recordList.getAdapter()).setRecords(records);
//                }
//
//            }
        });
    }

    private void refreshDialogData() {
        Cursor oldCursor;

        ListView machineList = new ListView(getContext());

        // Version avec table Machine
//        Cursor c = mDbMachine.getAllMachines(selectedTypes);
//
//        if (c == null || c.getCount() == 0) {
//            if (selectedTypes.size() == 0) {
//                KToast.warningToast(getActivity(), getResources().getText(R.string.selectExerciseTypeFirst).toString(), Gravity.BOTTOM, KToast.LENGTH_SHORT);
//            } else {
//                //Toast.makeText(getActivity(), R.string.createExerciseFirst, Toast.LENGTH_SHORT).show();
//                KToast.warningToast(getActivity(), getResources().getText(R.string.createExerciseFirst).toString(), Gravity.BOTTOM, KToast.LENGTH_SHORT);
//            }
//            machineList.setAdapter(null);
//        } else {
//            if (machineList.getAdapter() == null) {
//                MachineCursorAdapter mTableAdapter = new MachineCursorAdapter(getActivity(), c, 0, mDbMachine);
//                //MachineArrayFullAdapter lAdapter = new MachineArrayFullAdapter(v.getContext(),records);
//                machineList.setAdapter(mTableAdapter);
//            } else {
//                MachineCursorAdapter mTableAdapter = (MachineCursorAdapter) machineList.getAdapter();
//                oldCursor = mTableAdapter.swapCursor(c);
//                if (oldCursor != null) oldCursor.close();
//            }
//
//            ListView listView = foodListDialog.findViewById(R.id.listMachine);
//            listView.setAdapter(machineList.getAdapter());
//        }
    }

    private void refreshData() {
        View fragmentView = getView();
        if (fragmentView != null) {
            if (getProfile() != null) {
//                mDbRecord.setProfile(getProfile());
//
//                // Version avec table Machine
//                List<Machine> machineListArray = mDbMachine.getAllMachinesArray(selectedTypes);
//
//                /* Init machines list*/
//                machineEditAdapter = new MachineArrayFullAdapter(getContext(), machineListArray);
//                foodNameEdit.setAdapter(machineEditAdapter);
//
//                if (foodNameEdit.getText().toString().isEmpty()) {
//                    Record lLastRecord = mDbRecord.getLastRecord(getProfile());
//                    if (lLastRecord != null) {
//                        // Last recorded exercise
//                        setCurrentMachine(lLastRecord.getExercise());
//                    } else {
//                        // Getting the prefered default units.
//                        WeightUnit weightUnit = SettingsFragment.getDefaultWeightUnit(getActivity());
//                        DistanceUnit distanceUnit = SettingsFragment.getDefaultDistanceUnit(getActivity());
//
//                        // Default Values
//                        foodNameEdit.setText("");
//                        // Default Values
//                        workoutValuesInputView.setSets(1);
//                        workoutValuesInputView.setReps(10);
//                        workoutValuesInputView.setSeconds(60);
//                        workoutValuesInputView.setWeight(50, weightUnit);
//                        workoutValuesInputView.setDistance(10, distanceUnit);
//                        workoutValuesInputView.setDuration(600000);
//                        setCurrentMachine("");
//                        changeExerciseTypeUI(ExerciseType.STRENGTH, true);
//                    }
//                } else { // Restore on fragment restore.
//                    setCurrentMachine(foodNameEdit.getText().toString());
//                }
//
//                // Set Initial text
//                if (autoTimeCheckBox.isChecked()) {
//                    dateEdit.setText(DateConverter.currentDate(getContext()));
//                    timeEdit.setText(DateConverter.currentTime(getContext()));
//                }
//
//                // Set Table
//                updateRecordTable(foodNameEdit.getText().toString());
            }
        }
    }

    public void saveSharedParams() {
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("showDetails", this.detailsLayout.isShown());
        editor.apply();
    }

    public void restoreSharedParams() {
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
//        workoutValuesInputView.setRestTime(sharedPref.getInt("restTime2", 60));
//        workoutValuesInputView.activatedRestTime(sharedPref.getBoolean("restCheck", true));

        if (sharedPref.getBoolean("showDetails", false)) {
            detailsLayout.setVisibility(View.VISIBLE);
        } else {
            detailsLayout.setVisibility(View.GONE);
        }
        detailsExpandArrow.setImageResource(sharedPref.getBoolean("showDetails", false) ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
    }

    /*@Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden)
            refreshData();
    }*/
}
