package org.adaptlab.chpir.android.survey;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.activeandroid.Model;
import com.crashlytics.android.Crashlytics;

import org.adaptlab.chpir.android.survey.location.LocationServiceManager;
import org.adaptlab.chpir.android.survey.models.Grid;
import org.adaptlab.chpir.android.survey.models.Instrument;
import org.adaptlab.chpir.android.survey.models.Option;
import org.adaptlab.chpir.android.survey.models.Question;
import org.adaptlab.chpir.android.survey.models.Question.QuestionType;
import org.adaptlab.chpir.android.survey.models.Response;
import org.adaptlab.chpir.android.survey.models.Section;
import org.adaptlab.chpir.android.survey.models.Survey;
import org.adaptlab.chpir.android.survey.questionfragments.MultipleSelectGridFragment;
import org.adaptlab.chpir.android.survey.questionfragments.SingleSelectGridFragment;
import org.adaptlab.chpir.android.survey.roster.RosterActivity;
import org.adaptlab.chpir.android.survey.rules.InstrumentSurveyLimitPerMinuteRule;
import org.adaptlab.chpir.android.survey.rules.InstrumentSurveyLimitRule;
import org.adaptlab.chpir.android.survey.rules.InstrumentTimingRule;
import org.adaptlab.chpir.android.survey.rules.RuleBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric.sdk.android.Fabric;

public class SurveyFragment extends Fragment {
    public final static String EXTRA_INSTRUMENT_ID =
            "org.adaptlab.chpir.android.survey.instrument_id";
    public final static String EXTRA_QUESTION_NUMBER =
            "org.adaptlab.chpir.android.survey.question_number";
    public final static String EXTRA_SURVEY_ID =
            "org.adaptlab.chpir.android.survey.survey_id";
    public final static String EXTRA_PREVIOUS_QUESTION_IDS =
            "org.adaptlab.chpir.android.survey.previous_questions";
    public final static String EXTRA_PARTICIPANT_METADATA =
            "org.adaptlab.chpir.android.survey.metadata";
    public final static String EXTRA_QUESTIONS_TO_SKIP_IDS =
            "org.adaptlab.chpir.android.survey.questions_to_skip_ids";
    public final static String EXTRA_SECTION_ID =
            "org.adaptlab.chpir.android.survey.section_id";
    private static final String TAG = "SurveyFragment";
    private static final int REVIEW_CODE = 100;
    private static final int SECTION_CODE = 200;
    private static final Long REVIEW_PAGE_ID = -1L;
    private static final Map<String, Integer> mMenuItems;

    static {
        Map<String, Integer> menuItems = new HashMap<String, Integer>();
        menuItems.put(Response.SKIP, R.id.menu_item_skip);
        menuItems.put(Response.NA, R.id.menu_item_na);
        menuItems.put(Response.RF, R.id.menu_item_rf);
        menuItems.put(Response.DK, R.id.menu_item_dk);
        mMenuItems = Collections.unmodifiableMap(menuItems);
    }

    QuestionFragment mQuestionFragment;
    private Question mQuestion;
    private Instrument mInstrument;
    private Survey mSurvey;
    private int mQuestionNumber;
    private int mQuestionCount;
    private String mMetadata;
    private Question mResumeQuestion = null;
    private Grid mGrid;
    private Section mSection;
    // mPreviousQuestions is a Stack, however Android does not allow you
    // to save a Stack to the savedInstanceState, so it is represented as
    // an Integer array.
    private ArrayList<Integer> mPreviousQuestions;
    private ArrayList<Integer> mQuestionsToSkip;
    private ArrayList<Section> mSections;
    private List<Question> mQuestions;
    private HashMap<Question, Response> mResponses;
    private TextView mQuestionText;
    private TextView mQuestionIndex;
    private TextView mParticipantLabel;
    private ProgressBar mProgressBar;
    private LocationServiceManager mLocationServiceManager;
    private GestureDetector mGestureDetector;

    //drawer vars
    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    private String mDrawerTitle;
    private String mTitle;
    private String[] mSectionTitles;
    private boolean mNavDrawerSet = false;
    private boolean showSectionView = true;
    private HashMap<Question, List<Option>> mOptions;

    private void selectItem(int position) {
        if (mSections.get(position).questions().size() > 0) {
            if (mInstrument.getShowSectionsFragment()) {
                moveToSection(mSections.get(position));
            } else {
                mSection = mSections.get(position);
                mQuestion = mSection.questions().get(0);
                mQuestionNumber = mQuestion.getNumberInInstrument() - 1;
                refreshView();
            }
        }
        mDrawerList.setItemChecked(position, true);
        getActivity().setTitle(mInstrument.getTitle() + " : " + mSectionTitles[position]);
        mDrawerLayout.closeDrawer(mDrawerList);
        if (mSections.get(position).getRemoteId().equals(REVIEW_PAGE_ID)) {
            goToReviewPage();
        }
    }

    private void moveToSection(Section section) {
        mSection = section;
        mPreviousQuestions.add(mQuestionNumber);
        Intent i = new Intent(getActivity(), SectionActivity.class);
        Bundle args = new Bundle();
        args.putLong(EXTRA_SECTION_ID, section.getRemoteId());
        args.putLong(EXTRA_SURVEY_ID, mSurvey.getId());
        i.putExtras(args);
        startActivityForResult(i, SECTION_CODE);
    }

    private void updateQuestionText() {
        if (mQuestion.belongsToGrid()) {
            setGridLabelText(mQuestionText);
        } else {
            setQuestionText(mQuestionText);
        }
        mQuestionText.setTypeface(mInstrument.getTypeFace(getActivity().getApplicationContext()));
    }

    public void loadOrCreateQuestion() {
        mPreviousQuestions = new ArrayList<>();
        mQuestionsToSkip = new ArrayList<>();
        int questionNum = getActivity().getIntent().getIntExtra(EXTRA_QUESTION_NUMBER, -1);
        if (questionNum == -1) {
            mQuestion = mQuestions.get(0);
            mQuestionNumber = 0;
        } else {
            mQuestion = mQuestions.get(questionNum);
            mQuestionNumber = questionNum;
            for (int i = 0; i < mQuestionNumber; i++)
                mPreviousQuestions.add(i);
        }
        if (mQuestion.belongsToGrid()) {
            mGrid = mQuestion.getGrid();
        }
    }

    private void refreshView() {
        setParticipantLabel();
        updateQuestionCountLabel();
        updateQuestionText();
        createQuestionFragment();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == REVIEW_CODE) {
            int questionNum = data.getExtras().getInt(EXTRA_QUESTION_NUMBER);
            if (questionNum == Integer.MIN_VALUE) {
                checkForCriticalResponses();
            } else {
                Question question = mQuestions.get(questionNum);
                if (question != null) {
                    mQuestion = question;
                    mResumeQuestion = mQuestion;
                    mQuestionNumber = questionNum;
                } else {
                    checkForCriticalResponses();
                }
            }
        }
        if (resultCode == Activity.RESULT_OK && requestCode == SECTION_CODE) {
            Question previousQuestion = mQuestion;
            int questionNum = data.getExtras().getInt(EXTRA_QUESTION_NUMBER);
            Long instrumentId = data.getExtras().getLong(EXTRA_INSTRUMENT_ID);
            Long surveyId = data.getExtras().getLong(EXTRA_SURVEY_ID);
            ArrayList<Integer> previousQuestions = data.getExtras().getIntegerArrayList
                    (EXTRA_PREVIOUS_QUESTION_IDS);
            mQuestion = mQuestions.get(questionNum);
            mQuestionNumber = questionNum;
            mInstrument = Instrument.findByRemoteId(instrumentId);
            mSurvey = Model.load(Survey.class, surveyId);
            if (mQuestion.getSection() != null && mQuestion.getSection() == mSection)
                showSectionView = false;
            if (previousQuestions != null) mPreviousQuestions.addAll(previousQuestions);
            if (previousQuestion == mQuestion) showSectionView = false;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        AppUtil.authorize();
        setHasOptionsMenu(true);
        if (AppUtil.getContext() == null) AppUtil.setContext(getActivity());

        if (savedInstanceState != null) {
            mInstrument = Instrument.findByRemoteId(savedInstanceState.getLong
                    (EXTRA_INSTRUMENT_ID));
            if (!checkRules()) getActivity().finish();
            launchRosterSurvey();
            if (!mInstrument.isRoster()) {
                mSurvey = Survey.load(Survey.class, savedInstanceState.getLong(EXTRA_SURVEY_ID));
                mQuestionNumber = savedInstanceState.getInt(EXTRA_QUESTION_NUMBER);
                mPreviousQuestions = savedInstanceState.getIntegerArrayList
                        (EXTRA_PREVIOUS_QUESTION_IDS);
                mQuestionsToSkip = savedInstanceState.getIntegerArrayList(
                        EXTRA_QUESTIONS_TO_SKIP_IDS);
            }
        } else {
            Long instrumentId = getActivity().getIntent().getLongExtra(EXTRA_INSTRUMENT_ID, -1);
            mMetadata = getActivity().getIntent().getStringExtra(EXTRA_PARTICIPANT_METADATA);
            if (instrumentId == -1) return;
            mInstrument = Instrument.findByRemoteId(instrumentId);
            if (mInstrument == null) return;
            if (!checkRules()) getActivity().finish();
            launchRosterSurvey();
            if (!mInstrument.isRoster()) {
                loadOrCreateSurvey();
            }
        }

        if (AppUtil.PRODUCTION) {
            Fabric.with(getActivity(), new Crashlytics());
            Crashlytics.setString(getString(R.string.last_instrument), mInstrument.getTitle());
        }

        if (!mInstrument.isRoster()) {
            mQuestionCount = mInstrument.getQuestionCount();
            mQuestions = new ArrayList<>(mInstrument.getQuestionCount());
            new LoadQuestionsTask().execute(mInstrument);
        }

        if (AppUtil.getAdminSettingsInstance().getRecordSurveyLocation()) {
            if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission
                    .ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startLocationServices();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_survey, parent, false);
        mQuestionText = (TextView) v.findViewById(R.id.question_text);
        mParticipantLabel = (TextView) v.findViewById(R.id.participant_label);
        mQuestionIndex = (TextView) v.findViewById(R.id.question_index);
        mProgressBar = (ProgressBar) v.findViewById(R.id.progress_bar);
        mQuestionText.setTypeface(mInstrument.getTypeFace(getActivity().getApplicationContext()));
        ActivityCompat.invalidateOptionsMenu(getActivity());
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) actionBar.setTitle(mInstrument.getTitle());
        LinearLayout swipeView = (LinearLayout) v.findViewById(
                R.id.linear_layout_for_question_index);
        mGestureDetector = new GestureDetector(getActivity(), new GestureListener());
        swipeView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        registerLocationReceiver();
    }

    private void registerLocationReceiver() {
        if (AppUtil.getAdminSettingsInstance().getRecordSurveyLocation() &&
                ContextCompat.checkSelfPermission(getActivity(), Manifest.permission
                        .ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getActivity().registerReceiver(mLocationServiceManager.mLocationReceiver,
                    new IntentFilter(LocationServiceManager.ACTION_LOCATION));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mQuestion != null) {
            if (mResumeQuestion == mQuestion)
                mQuestionNumber = mQuestion.getNumberInInstrument() - 1;
            refreshView();
            showSectionView = true;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(EXTRA_INSTRUMENT_ID, mInstrument.getRemoteId());
        if (mSurvey != null) outState.putLong(EXTRA_SURVEY_ID, mSurvey.getId());
        outState.putInt(EXTRA_QUESTION_NUMBER, mQuestionNumber);
        outState.putIntegerArrayList(EXTRA_PREVIOUS_QUESTION_IDS, mPreviousQuestions);
        outState.putIntegerArrayList(EXTRA_QUESTIONS_TO_SKIP_IDS, mQuestionsToSkip);
    }

    @Override
    public void onStop() {
        if (AppUtil.getAdminSettingsInstance().getRecordSurveyLocation()
                && ContextCompat.checkSelfPermission(getActivity(), Manifest.permission
                .ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && mLocationServiceManager != null
                && mLocationServiceManager.mLocationReceiver != null) {
            getActivity().unregisterReceiver(mLocationServiceManager.mLocationReceiver);
        }
        super.onStop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_survey, menu);
        if (!mNavDrawerSet) {
            setupNavigationDrawer();
        }
    }

    private void setupNavigationDrawer() {
        setNavigationDrawerItems();
        mTitle = mDrawerTitle = mInstrument.getTitle();
        mDrawerLayout = (DrawerLayout) getActivity().findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) getActivity().findViewById(R.id.left_drawer);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        mDrawerList.setAdapter(new ArrayAdapter<>(getActivity(), R.layout.drawer_list_item,
                mSectionTitles));
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
        mDrawerToggle = new ActionBarDrawerToggle(
                getActivity(),
                mDrawerLayout,
                R.string.drawer_open,
                R.string.drawer_close
        ) {

            public void onDrawerOpened(View drawerView) {
                ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(mDrawerTitle);
                }
                getActivity().invalidateOptionsMenu();
            }

            public void onDrawerClosed(View view) {
                ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(mTitle);
                }
                getActivity().invalidateOptionsMenu();
            }
        };
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mNavDrawerSet = true;
    }

    private void setNavigationDrawerItems() {
        mSections = new ArrayList<>();
        mSections.addAll(mInstrument.sections());
        if (mInstrument.getDirectReviewNavigation()) {
            Section reviewSection = Section.findByRemoteId(REVIEW_PAGE_ID);
            if (reviewSection == null) {
                reviewSection = new Section();
                reviewSection.setRemoteId(REVIEW_PAGE_ID);
                reviewSection.setTitle(getActivity().getString(R.string.review_section_title));
                reviewSection.setInstrumentRemoteId(mInstrument.getRemoteId());
                reviewSection.save();
            }
            if (!mSections.contains(reviewSection)) {
                mSections.add(reviewSection);
            }
        }
        mSectionTitles = new String[mSections.size()];
        for (int i = 0; i < mSections.size(); i++) {
            mSectionTitles[i] = mSections.get(i).getTitle();
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mQuestion != null) {
            menu.findItem(R.id.menu_item_previous).setEnabled(!isFirstQuestion());
            menu.findItem(R.id.menu_item_next).setVisible(!isLastQuestion())
                    .setEnabled(hasValidResponse());
            for (String key : mMenuItems.keySet()) {
                if (!mInstrument.getSpecialOptionStrings().contains(key)) {
                    menu.findItem(mMenuItems.get(key)).setVisible(false).setEnabled(false);
                } else {
                    if (key.equals(Response.SKIP)) {
                        menu.findItem(mMenuItems.get(key)).setEnabled(hasValidResponse());
                    }
                }
            }
            menu.findItem(R.id.menu_item_finish).setVisible(isLastQuestion())
                    .setEnabled(hasValidResponse());
            showSpecialResponseSelection(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.menu_item_previous:
                moveToPreviousQuestion();
                return true;
            case R.id.menu_item_next:
                unSkipAndMoveToNextQuestion();
                return true;
            case R.id.menu_item_skip:
                setSpecialResponse(Response.SKIP);
                proceedToNextQuestion();
                return true;
            case R.id.menu_item_rf:
                setSpecialResponse(Response.RF);
                return true;
            case R.id.menu_item_na:
                setSpecialResponse(Response.NA);
                return true;
            case R.id.menu_item_dk:
                setSpecialResponse(Response.DK);
                return true;
            case R.id.menu_item_finish:
                finishSurvey();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean isFirstQuestion() {
        return mQuestionNumber == 0;
    }

    public boolean isLastQuestion() {
        if (mQuestion.belongsToGrid()) {
            Question lastGridQuestion = mGrid.questions().get(mGrid.questions().size() - 1);
            return mQuestionCount == lastGridQuestion.getNumberInInstrument();
        } else {
            return mQuestionCount == mQuestionNumber + 1;
        }
    }

    public boolean hasValidResponse() {
        return !(mQuestionFragment != null && mQuestionFragment.getResponse() != null) ||
                mQuestionFragment.getResponse().isValid();
    }

    /*
     * Give a visual indication when a special response is selected
     */
    public void showSpecialResponseSelection(Menu menu) {
        if (mQuestionFragment != null && mQuestionFragment.getSpecialResponse() != null && menu
                != null) {
            if (mQuestionFragment.getSpecialResponse().equals(Response.SKIP)) {
                menu.findItem(R.id.menu_item_skip).setIcon(R.drawable.ic_menu_item_sk_selected);
            } else if (mQuestionFragment.getSpecialResponse().equals(Response.RF)) {
                menu.findItem(R.id.menu_item_rf).setIcon(R.drawable.ic_menu_item_rf_selected);
            } else if (mQuestionFragment.getSpecialResponse().equals(Response.NA)) {
                menu.findItem(R.id.menu_item_na).setIcon(R.drawable.ic_menu_item_na_selected);
            } else if (mQuestionFragment.getSpecialResponse().equals(Response.DK)) {
                menu.findItem(R.id.menu_item_dk).setIcon(R.drawable.ic_menu_item_dk_selected);
            }
        }
    }

    private boolean checkRules() {
        return new RuleBuilder(getActivity())
                .addRule(new InstrumentSurveyLimitRule(mInstrument,
                        getActivity().getString(R.string.rule_failure_instrument_survey_limit)))
                .addRule(new InstrumentTimingRule(mInstrument, getResources().getConfiguration()
                        .locale,
                        getActivity().getString(R.string.rule_failure_survey_timing)))
                .addRule(new InstrumentSurveyLimitPerMinuteRule(mInstrument,
                        getActivity().getString(R.string.rule_instrument_survey_limit_per_minute)))
                .showToastOnFailure(true)
                .checkRules()
                .getResult();
    }

    private void launchRosterSurvey() {
        if (mInstrument.isRoster()) {
            Intent i = new Intent(getActivity(), RosterActivity.class);
            i.putExtra(RosterActivity.EXTRA_INSTRUMENT_ID, mInstrument.getRemoteId());
            i.putExtra(RosterActivity.EXTRA_PARTICIPANT_METADATA, mMetadata);
            getActivity().startActivity(i);
            getActivity().finish();
        }
    }

    public void loadOrCreateSurvey() {
        Long surveyId = getActivity().getIntent().getLongExtra(EXTRA_SURVEY_ID, -1);
        if (surveyId == -1) {
            mSurvey = new Survey();
            mSurvey.setInstrumentRemoteId(mInstrument.getRemoteId());
            mSurvey.setMetadata(mMetadata);
            mSurvey.setProjectId(mInstrument.getProjectId());
            mSurvey.save();
        } else {
            mSurvey = Model.load(Survey.class, surveyId);
        }
    }

    private void startLocationServices() {
        mLocationServiceManager = LocationServiceManager.get(getActivity());
        mLocationServiceManager.startLocationUpdates();
    }

    private void proceedToNextQuestion() {
        if (isLastQuestion()) {
            finishSurvey();
        } else {
            moveToNextQuestion();
        }
    }

    private void unSkipAndMoveToNextQuestion() {
        if (mQuestionFragment.getSpecialResponse().equals(Response.SKIP)) {
            mQuestionFragment.setSpecialResponse("");
        }
        proceedToNextQuestion();
    }

    /*
     * Place the question fragment for the corresponding mQuestion
     * on the view in the question_container.
     */
    protected void createQuestionFragment() {
        if (mQuestion == null || mSurvey == null) {
            loadOrCreateQuestion();
            loadOrCreateSurvey();
        }
        if (mInstrument.getShowSectionsFragment() && mQuestion.isFirstQuestionInSection() &&
                showSectionView) {
            moveToSection(mQuestion.getSection());
        } else {
            if (mQuestion.belongsToGrid()) {
                createGridFragment();
            } else {
                FragmentManager fm = getChildFragmentManager();
                mQuestionFragment = (QuestionFragment) QuestionFragmentFactory
                        .createQuestionFragment(mQuestion);
                switchOutFragments(fm);
                changeOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    }

    private void createGridFragment() {
        if (mQuestion.getQuestionType() == QuestionType.SELECT_ONE) {
            mQuestionFragment = new SingleSelectGridFragment();
        } else {
            mQuestionFragment = new MultipleSelectGridFragment();
        }
        Bundle bundle = new Bundle();
        bundle.putLong(GridFragment.EXTRA_GRID_ID, mQuestion.getGrid().getRemoteId());
        bundle.putLong(GridFragment.EXTRA_SURVEY_ID, mSurvey.getId());
        mQuestionFragment.setArguments(bundle);
        FragmentManager fm = getChildFragmentManager();
        switchOutFragments(fm);
        changeOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    private void changeOrientation(int orientation) {
        getActivity().setRequestedOrientation(orientation);
    }

    private void switchOutFragments(FragmentManager fm) {
        if (fm.findFragmentById(R.id.question_container) == null) {
            fm.beginTransaction().add(R.id.question_container, mQuestionFragment).commit();
        } else {
            fm.beginTransaction().replace(R.id.question_container, mQuestionFragment).commit();
        }
        mSurvey.setLastQuestion(mQuestion);
        mSurvey.save();
        removeTextFocus();
    }

    public Question getQuestion() {
        return mQuestion;
    }

    public Survey getSurvey() {
        return mSurvey;
    }

    public HashMap<Question, Response> getResponses() {
        return mResponses;
    }

    public HashMap<Question, List<Option>> getOptions() {
        return mOptions;
    }

    private void setGridLabelText(TextView view) {
        view.append(styleTextWithHtml(mGrid.getText()));
    }

    /*
     * This will remove the focus of the input as the survey is
     * traversed.  If this is not called, then it will be possible
     * for someone to change the answer to a question that they are
     * not currently viewing.
     */
    private void removeTextFocus() {
        if (getActivity().getCurrentFocus() != null) {
            InputMethodManager inputManager = (InputMethodManager) getActivity().getSystemService
                    (Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken()
                    , InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    /*
     * If a question has a skip pattern, then read the response
     * when pressing the "next" button.  If the index of the response
     * is able to have a skip pattern, then set the next question to
     * the question indicated by the skip pattern.  "Other" responses
     * cannot have skip patterns, and the question is just set to the
     * next question in the sequence.
     */
    private Question getNextQuestion(int questionIndex) {
        Question nextQuestion;
        Response response = mResponses.get(mQuestion);
        if (response == null) {
            nextQuestion = nextQuestionHelper(questionIndex);
        } else {
            if (!TextUtils.isEmpty(response.getSpecialResponse())) {
                Option specialOption = mQuestion.specialOptionByText(response.getSpecialResponse
                        ().trim());
                if (specialOption != null && specialOption.getNextQuestion() != null) {
                    nextQuestion = specialOption.getNextQuestion();
                } else {
                    nextQuestion = nextQuestionHelper(questionIndex);
                }
            } else if (Question.AnyResponseQuestions.contains(mQuestion.getQuestionType())) {
                Option anyResponseOption = mQuestion.anyResponseOption();
                if (!TextUtils.isEmpty(response.getText()) && anyResponseOption != null &&
                        anyResponseOption.getNextQuestion() != null) {
                    nextQuestion = anyResponseOption.getNextQuestion();
                } else {
                    nextQuestion = nextQuestionHelper(questionIndex);
                }
            } else if (mQuestion.hasSkipPattern()) {
                try {
                    int responseIndex = Integer.parseInt(response.getText());
                    addQuestionsToSkip(responseIndex);
                    nextQuestion = getNextQuestionForSkipPattern(questionIndex, responseIndex);
                } catch (NumberFormatException nfe) {
                    nextQuestion = getNextQuestionWhenNumberFormatException(questionIndex);
                }
            } else {
                nextQuestion = nextQuestionHelper(questionIndex);
            }
        }
        return getNextUnSkippedQuestion(nextQuestion);
    }

    private Question getNextQuestionWhenNumberFormatException(int questionIndex) {
        Question nextQuestion;
        nextQuestion = nextQuestionHelper(questionIndex);
        Log.wtf(TAG, "Received a non-numeric skip response index for " +
                mQuestion.getQuestionIdentifier());
        return nextQuestion;
    }

    private void addQuestionsToSkip(int responseIndex) {
        if (responseIndex < mQuestion.defaultOptions().size()) {
            Option selectedOption = mQuestion.defaultOptions().get(responseIndex);
            for (Question skipQuestion : selectedOption.questionsToSkip()) {
                mQuestionsToSkip.add(skipQuestion.getNumberInInstrument());
            }
        }
    }

    private Question getNextQuestionForSkipPattern(int questionIndex, int responseIndex) {
        Question nextQuestion;
        if (responseIndex < mQuestion.defaultOptions().size() && mQuestion.defaultOptions().get
                (responseIndex).getNextQuestion() != null) {
            nextQuestion = mQuestion.defaultOptions().get(responseIndex).getNextQuestion();
            mQuestionNumber = nextQuestion.getNumberInInstrument() - 1;
        } else {
            nextQuestion = nextQuestionHelper(questionIndex);
        }
        return nextQuestion;
    }

    private Question getNextUnSkippedQuestion(Question nextQuestion) {
        if (mQuestionsToSkip.contains(nextQuestion.getNumberInInstrument())) {
            if (isLastQuestion()) {
                finishSurvey();
            } else {
                nextQuestion = nextQuestionHelper(nextQuestion.getNumberInInstrument() - 1);
                nextQuestion = getNextUnSkippedQuestion(nextQuestion);
            }
        }
        return nextQuestion;
    }

    private Question nextQuestionHelper(int index) {
        mQuestionNumber = index + 1;
        return mQuestions.get(mQuestionNumber);
    }

    private void clearSkipsForCurrentQuestion() {
        if (!mQuestionsToSkip.isEmpty()) {
            for (Question question : mQuestion.questionsToSkip()) {
                mQuestionsToSkip.remove(Integer.valueOf(question.getNumberInInstrument()));
            }
        }
    }

    /*
     * Switch out the next question with a fragment from the
     * QuestionFragmentFactory.  Increment the question to
     * the next question.
     */
    public void moveToNextQuestion() {
        if (mQuestion.firstInGrid()) {
            setQuestionToLastInGrid();
        }
        if (mQuestionNumber < mQuestionCount - 1) {
            mPreviousQuestions.add(mQuestionNumber);
            mQuestion = getNextQuestion(mQuestionNumber);
            if (mQuestion.getGrid() != null) {
                mGrid = mQuestion.getGrid();
            }
            createQuestionFragment();
            if (!setQuestionText(mQuestionText)) {
                setSpecialResponse(Response.LOGICAL_SKIP);
                moveToNextQuestion();
            }
        } else if (isLastQuestion() && !setQuestionText(mQuestionText)) {
            finishSurvey();
        }
        mQuestionNumber = mQuestion.getNumberInInstrument() - 1;
        updateQuestionCountLabel();
    }

    private void setQuestionToLastInGrid() {
        mQuestionNumber = mGrid.questions().get(mGrid.questions().size() - 1)
                .getNumberInInstrument() - 1;
        mPreviousQuestions.add(mQuestion.getNumberInInstrument() - 1);
        mQuestion = mGrid.questions().get(mGrid.questions().size() - 1);
    }

    /*
     * Move to previous question.  Takes into account if
     * this question is following up another question.  If
     * this question is not a follow up question, just move
     * to the previous question in the sequence.
     */
    public void moveToPreviousQuestion() {
        if (mQuestionNumber > 0 && mQuestionNumber < mQuestionCount) {
            mQuestionNumber = mPreviousQuestions.remove(mPreviousQuestions.size() - 1);
            mQuestion = mQuestions.get(mQuestionNumber);
            if (mQuestion.getGrid() != null) {
                mGrid = mQuestion.getGrid();
            }
            showSectionView = false;
            createQuestionFragment();
            if (!setQuestionText(mQuestionText)) {
                moveToPreviousQuestion();
            }
            if (mResponses.get(mQuestion) != null &&
                    !mResponses.get(mQuestion).getText().isEmpty()) {
                clearSkipsForCurrentQuestion();
            }
        }

        updateQuestionCountLabel();
    }

    /*
    * Destroy this activity, and save the survey and mark it as
    * complete.  Send to server if network is available.
    */
    public void finishSurvey() {
        if (AppUtil.getAdminSettingsInstance().getRecordSurveyLocation()) {
            setSurveyLocation();
        }
        if (mSurvey.emptyResponses().size() > 0) {
            goToReviewPage();
        } else {
            checkForCriticalResponses();
        }
    }

    private void checkForCriticalResponses() {
        final List<String> criticalResponses = getCriticalResponses();
        if (criticalResponses.size() > 0) {
            String[] criticalQuestions = new String[criticalResponses.size()];
            for (int k = 0; k < criticalResponses.size(); k++) {
                criticalQuestions[k] = Question.findByQuestionIdentifier(criticalResponses.get(k)
                ).getNumberInInstrument()
                        + ": " + criticalResponses.get(k);
            }

            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            View content = LayoutInflater.from(getActivity()).inflate(R.layout
                    .critical_responses_dialog, null);
            ListView listView = (ListView) content.findViewById(R.id.critical_list);
            listView.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout
                    .simple_selectable_list_item, criticalQuestions));

            builder.setTitle(R.string.critical_message_title)
                    .setMessage(mInstrument.getCriticalMessage())
                    .setView(content)
                    .setPositiveButton(R.string.submit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int button) {
                            mSurvey.setCriticalResponses(true);
                            completeSurvey();
                        }
                    })
                    .setNegativeButton(R.string.review, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mQuestion = Question.findByQuestionIdentifier(criticalResponses.get(0));
                            mQuestionNumber = mQuestion.getNumberInInstrument() - 1;
                            refreshView();
                        }
                    });
            final AlertDialog criticalDialog = builder.create();
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    mQuestion = Question.findByQuestionIdentifier(criticalResponses.get(position));
                    mQuestionNumber = mQuestion.getNumberInInstrument() - 1;
                    refreshView();
                    criticalDialog.dismiss();
                }
            });
            criticalDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    criticalDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                            .setBackgroundColor(getResources().getColor(R.color.green));
                    criticalDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                            .setBackgroundColor(getResources().getColor(R.color.red));
                }
            });
            criticalDialog.show();
        } else {
            mSurvey.setCriticalResponses(false);
            completeSurvey();
        }
    }

    private void completeSurvey() {
        mSurvey.setAsComplete(true);
        mSurvey.save();
        getActivity().finish();
    }

    private List<String> getCriticalResponses() {
        List<String> criticalQuestions = new ArrayList<String>();
        if (mInstrument.criticalQuestions().size() > 0) {
            for (Question question : mInstrument.criticalQuestions()) {
                Response response = mResponses.get(question);
                Set<String> optionSet = new HashSet<String>();
                Set<String> responseSet = new HashSet<String>();
                if (response != null) {
                    for (Option option : question.criticalOptions()) {
                        optionSet.add(Integer.toString(question.defaultOptions().indexOf(option)));
                    }
                    if (!TextUtils.isEmpty(response.getText())) {
                        responseSet.addAll(Arrays.asList(response.getText().split(",")));
                    }
                    optionSet.retainAll(responseSet);
                }
                if (optionSet.size() > 0) {
                    criticalQuestions.add(question.getQuestionIdentifier());
                }
            }
        }
        return criticalQuestions;
    }

    private void goToReviewPage() {
        Intent i = new Intent(getActivity(), ReviewPageActivity.class);
        Bundle b = new Bundle();
        b.putLong(ReviewPageFragment.EXTRA_REVIEW_SURVEY_ID, mSurvey.getId());
        i.putExtras(b);
        startActivityForResult(i, REVIEW_CODE);
    }

    private void setSurveyLocation() {
        mSurvey.setLatitude(mLocationServiceManager.getLatitude());
        mSurvey.setLongitude(mLocationServiceManager.getLongitude());
    }

    /*
     * If this question is a follow up question, then attempt
     * to get the response to the question that is being followed up on.
     *
     * If the question being followed up on was skipped by the user,
     * then return false. This gives the calling function an opportunity
     * to handle this accordingly.  Likely this will involve skipping
     * the question that is a follow up question.
     *
     * If this question is not a following up question, then just
     * set the text as normal.
     */
    private boolean setQuestionText(TextView text) {
        appendInstructions(text);

        if (mQuestion.isFollowUpQuestion()) {
            String followUpText = mQuestion.getFollowingUpText(mSurvey, getActivity());

            if (followUpText == null) {
                return false;
            } else {
                text.append(styleTextWithHtml(followUpText));
            }
        } else {
            text.append(styleTextWithHtml(mQuestion.getText()));
        }
        return true;
    }

    /*
     * If this question has instructions, append and add new line
     */
    private void appendInstructions(TextView text) {
        if (mQuestion.getInstructions() != null) {
            text.setText(styleTextWithHtml(mQuestion.getInstructions() + "<br /><br />"));
        } else {
            text.setText("");
        }
    }

    private Spanned styleTextWithHtml(String text) {
        return Html.fromHtml(text);
    }

    /*
     * Save the special response field and clear the current
     * response if there is one.
     */
    private void setSpecialResponse(String response) {
        mQuestionFragment.setSpecialResponse(response);
        if (isAdded()) {
            ActivityCompat.invalidateOptionsMenu(getActivity());
        }
    }

    private void setParticipantLabel() {
        String surveyMetaData = mSurvey.getMetadata();
        if (!TextUtils.isEmpty(surveyMetaData)) {
            try {
                JSONObject metadata = new JSONObject(surveyMetaData);
                if (metadata.has("survey_label")) {
                    mParticipantLabel.setText(metadata.getString("survey_label"));
                }
            } catch (JSONException er) {
                Log.e(TAG, er.getMessage());
            }
        }
    }

    private void updateQuestionCountLabel() {
        if (mQuestion.belongsToGrid()) {
            mQuestionIndex.setText((mQuestionNumber + 1) + " - " + (mQuestionNumber + mGrid
                    .questions().size()) + " " + getString(R.string.of) + " " + mQuestionCount);
        } else {
            mQuestionIndex.setText((mQuestionNumber + 1) + " " + getString(R.string.of) + " " +
                    mQuestionCount);
        }
        mProgressBar.setProgress((int) (100 * (mQuestionNumber + 1) / (float) mQuestionCount));

        if (isAdded()) {
            ActivityCompat.invalidateOptionsMenu(getActivity());
        }
    }

    private class LoadQuestionsTask extends AsyncTask<Instrument, Void, List<Question>> {

        ProgressDialog progressDialog;

        @Override
        protected List<Question> doInBackground(Instrument... params) {
            return params[0].questions();
        }

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(getActivity(),
                    getString(R.string.instrument_loading_progress_header),
                    getString(R.string.background_process_progress_message)
            );
        }

        @Override
        protected void onPostExecute(List<Question> questions) {
            mQuestions = questions;
            new LoadResponsesTask().execute(mSurvey);
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }
    }

    private class LoadResponsesTask extends AsyncTask<Survey, Void, HashMap<Question, Response>> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(getActivity(),
                    getString(R.string.instrument_loading_progress_header),
                    getString(R.string.background_process_progress_message)
            );
        }

        @Override
        protected HashMap<Question, Response> doInBackground(Survey... params) {
            return params[0].responsesMap();
        }

        @Override
        protected void onPostExecute(HashMap<Question, Response> responses) {
            mResponses = responses;
            new LoadOptionsTask().execute(mInstrument);
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }
    }

    private class LoadOptionsTask extends AsyncTask<Instrument, Void, HashMap<Question,
            List<Option>>> {

        ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = ProgressDialog.show(getActivity(),
                    getString(R.string.instrument_loading_progress_header),
                    getString(R.string.background_process_progress_message)
            );
        }

        @Override
        protected HashMap<Question, List<Option>> doInBackground(Instrument... params) {
            return params[0].optionsMap();
        }

        @Override
        protected void onPostExecute(HashMap<Question, List<Option>> options) {
            mOptions = options;
            loadOrCreateQuestion();
            ActivityCompat.invalidateOptionsMenu(getActivity());
            refreshView();
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            selectItem(position);
        }
    }

    public class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private float MINIMUM_FLING_DISTANCE = 100;

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float
                velocityY) {
            float horizontalDifference = event2.getX() - event1.getX();
            float absoluteHorizontalDifference = Math.abs(horizontalDifference);
            if (absoluteHorizontalDifference > MINIMUM_FLING_DISTANCE) {
                if (horizontalDifference > 0) {
                    moveToPreviousQuestion();
                } else {
                    unSkipAndMoveToNextQuestion();
                }
            }
            return true;
        }

        @Override
        public boolean onDown(MotionEvent event) {
            return true;
        }
    }
}