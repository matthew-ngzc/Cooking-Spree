package com.game.cookingspree;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.app.AlertDialog;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.game.cookingspree.util.PrefsHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameActivity extends BaseActivity implements
        GameManager.GameListener,
        IngredientFetchWorker.ingredientFetchListener,
        IngredientBasketFiller.BasketFillListener,
        PotFunctions.PotListener
        {

    private final Handler moveHandler = new Handler(Looper.getMainLooper());
    private final Map<View, Runnable> heldMovementCallbacks = new HashMap<>();
    private final HeldDirections heldDirections = new HeldDirections();
    private final SessionCallbacks sessionCallbacks = new SessionCallbacks();
    private final SessionComposer sessionComposer = new SessionComposer();
    protected final PauseState pauseState = new PauseState();
    private final FinalizationGate gameOverFinalization = new FinalizationGate();
    protected MediaPlayer mediaPlayer;
    protected GameManager gameManager;
    private OrderAdapter orderAdapter;
            private TextView scoreTextView;
    private TextView deadProcessCountTextView;

    protected Game game;

    private List<ImageView> inventoryViews;
    private List<ImageView> availableIngredientsViews;
    private LinearLayout swapOptionsLayout;

    private IngredientFetchWorker ingredientFetcher;
    private View ingredientBlocker;
    private PlayerInventory playerInventory;
    private int selectedIngredientIndex=-1;
    private int selectedSwapIndex=-1;
    private final int maxIngredients=3;
    private PotThreadPool potThreadPool;
    private ImageView playerInventoryView;
    //private SharedPreferences sharedPreferences;
    private BasketManager basketManager;
    private boolean loadSucceededForTest;
    private boolean restoreWasIsolatedForTest;
    private AlertDialog recoveryDialog;

    protected int getGameLayoutResource() { return R.layout.activity_game; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(getGameLayoutResource());
            //sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
            setupJoystickSizeListener(
                    findViewById(R.id.joystickSizeGroup),
                    R.id.smallSize,
                    R.id.largeSize,
                    scale -> applyJoystickScale(findViewById(android.R.id.content))
            );

            enableImmersiveMode();
            mediaPlayer = setupMediaPlayer(R.raw.overcooked);
            setupVolumeSeekBar(findViewById(R.id.volumeSeekBar), mediaPlayer);
            initializeGameComponents();
            setupInteractButton();
            setupPauseMenuButtons();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate: " + e.getMessage(), e);
            finish();
        }
    }

    protected void setupInteractButton() {
        Button interactButton = findViewById(R.id.interactButton);
        Log.d("Interact", "Setting up interact button listener");
        interactButton.setOnClickListener(v -> {
            if (isGameplayInputAllowed() && game != null) {
                game.interact();
                updatePlayerInventoryView();
            } else {
                Log.w("Interact", "Game not initialized yet!");
            }
        });
        Log.d("Interact", "Interact listener assigned");
    }
    protected void setupPauseMenuButtons(){
        // Link buttons
        Button togglePauseButton = findViewById(R.id.togglePauseButton);
        togglePauseButton.setText(R.string.pause); // Default state
        LinearLayout pauseMenu = findViewById(R.id.pauseMenu);
        LinearLayout settingsMenu = findViewById(R.id.SettingsMenu);
        Button resume = findViewById(R.id.btnResume);
        Button save = findViewById(R.id.btnSave);
        Button settings = findViewById(R.id.btnSettings);
        Button mainMenuButton = findViewById(R.id.btnMainMenu);
        ImageButton back = findViewById(R.id.backButton);

        // Set listeners
        togglePauseButton.setOnClickListener(v -> {
            if (gameManager.isGameOver()) return; // Don’t allow toggling if game is over

            if (gameManager.isRunning()) {
                gameManager.pauseGame();
                pauseMenu.setVisibility(View.VISIBLE);
                cancelHeldMovement();
            }
        });
        resume.setOnClickListener(v -> {
            if (gameManager.isGameOver()) return;
            gameManager.resumeGame();
            pauseMenu.setVisibility(gameManager.isRunning() ? View.GONE : View.VISIBLE);
        });
        save.setOnClickListener(v -> {
            boolean saved = saveGameState();
            Toast.makeText(this, saved ? "Game saved" : "Unable to save game", Toast.LENGTH_SHORT).show();
        });
        settings.setOnClickListener(v -> {
            settingsMenu.setVisibility(View.VISIBLE);
            pauseMenu.setVisibility(GONE);
            back.setVisibility(VISIBLE);
        });
        mainMenuButton.setOnClickListener(v -> {
            Intent intent = new Intent(GameActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish(); // Close the current activity
        });
        back.setOnClickListener(v -> {
            settingsMenu.setVisibility(View.GONE);
            pauseMenu.setVisibility(View.VISIBLE);
            back.setVisibility(View.GONE);
        });
    }

    private boolean saveGameState() {
        try {
            if (gameManager == null || gameManager.isGameOver()
                    || !pauseState.isPaused(PauseState.Reason.MANUAL_MENU)) return false;
            GameSaveSnapshot snapshot = GameSaveSnapshot.capture(game, gameManager, playerInventory);
            Map<String, Object> values = snapshot.toLegacyValues();
            GameSaveParser.parse(values, game.getTables().size(), game.getPots().size(),
                    game.getMapWidth(), game.getMapHeight(),
                    game.getPots().get(0).getPotFunctions().getMaximumProgressTicks(), game::isTraversableTile);
            return PrefsHelper.writeGameSaveValues(values, stored -> {
                try {
                    GameSaveSnapshot verified = GameSaveParser.parse(stored,
                            game.getTables().size(), game.getPots().size(), game.getMapWidth(), game.getMapHeight(),
                            game.getPots().get(0).getPotFunctions().getMaximumProgressTicks(), game::isTraversableTile);
                    return snapshot.gameVersion.equals(verified.gameVersion)
                            && GameVersionPolicy.compatibility(verified.gameVersion, BuildConfig.VERSION_NAME)
                            == GameVersionPolicy.Compatibility.COMPATIBLE;
                } catch (GameSaveParser.InvalidSaveException invalid) {
                    return false;
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error saving game snapshot", e);
            return false;
        }
    }

    private void loadGameState() {
        View activityContent = findViewById(android.R.id.content);
        activityContent.setVisibility(INVISIBLE);
        Map<String, ?> stored = getSharedPreferences("GameSave", MODE_PRIVATE).getAll();
        Object rawVersion = stored.get("gameVersion");
        String savedVersion = rawVersion instanceof String ? (String) rawVersion : null;
        if (GameVersionPolicy.compatibility(savedVersion, BuildConfig.VERSION_NAME)
                != GameVersionPolicy.Compatibility.COMPATIBLE) {
            String copy = getString(R.string.incompatible_save_message,
                    GameVersionPolicy.displayVersion(savedVersion), BuildConfig.VERSION_NAME);
            rejectLoadedSave(copy);
            return;
        }

        boolean loaded = false;
        List<String> basketContentsBeforeRestore = basketManager.getContentsSnapshotForTest();
        try {
            GameSaveSnapshot candidate = GameSaveParser.parse(
                    stored, game.getTables().size(),
                    game.getPots().size(), game.getMapWidth(), game.getMapHeight(),
                    game.getPots().get(0).getPotFunctions().getMaximumProgressTicks(), game::isTraversableTile);

            // No live state is touched until the entire versioned save has passed validation.
            game.getPlayer().setPosition(candidate.playerX, candidate.playerY);
            if (getIntent().getBooleanExtra("failRestoreAfterPositionForTest", false)) {
                throw new IllegalStateException("Injected restore application failure");
            }
            gameManager.setScore(candidate.score);
            if (!candidate.terminal) gameManager.setDeadProcessCount(candidate.deadProcessCount);
            playerInventory.getAndRemoveItem();
            if (candidate.heldItem != null) playerInventory.grabItem(createSavedItem(candidate.heldItem));

            List<Table> tables = game.getTables();
            for (int i = 0; i < tables.size(); i++) {
                GameSaveSnapshot.SavedItem item = candidate.tableItems.get(i);
                tables.get(i).clearItem();
                if (item != null) tables.get(i).placeItem(createSavedItem(item));
            }
            for (GameSaveSnapshot.SavedOrder saved : candidate.orders) {
                gameManager.addProcessDirectly(Order.generateRandomOrder(findRecipe(saved.recipeName),
                        saved.limitSeconds, saved.remainingSeconds));
            }
            List<Pot> pots = game.getPots();
            for (int i = 0; i < pots.size(); i++) pots.get(i).restoreForLoad(candidate.pots.get(i));

            // All restored cooking state is installed before any resumed worker is submitted.
            for (int i = 0; !candidate.terminal && i < pots.size(); i++) {
                GameSaveSnapshot.SavedPot saved = candidate.pots.get(i);
                if (saved.state != Pot.State.COOKING) continue;
                Pot pot = pots.get(i);
                Recipe recipe = findRecipe(saved.recipeName);
                if (recipe == null) recipe = new Recipe("Waste", new ArrayList<>());
                final Recipe resumeRecipe = recipe;
                potThreadPool.submit(() -> {
                    pot.getPotFunctions().restartCooking(resumeRecipe, this);
                    if (Thread.currentThread().isInterrupted() || !sessionCallbacks.isOpen()) return;
                    try {
                        while (!pauseState.runIfRunning(() -> pot.setState(Pot.State.DONE.name()))) {
                            if (!pauseState.awaitActiveDuration(0)) return;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            updatePlayerInventoryView();
            updateScoreDisplay(candidate.score);
            updateDeadProcessCountDisplay(candidate.deadProcessCount);
            if (orderAdapter != null) orderAdapter.updateProcesses(gameManager.getActiveProcesses());
            updateMediaPlaybackSpeed(candidate.deadProcessCount);
            // LOAD is held before composition starts order/fill owners. This assertion seam
            // records that restored orders were installed while timers stayed stopped and the
            // initial basket filler made no mutation before the candidate was fully applied.
            restoreWasIsolatedForTest = pauseState.isPaused(PauseState.Reason.LOAD)
                    && !gameManager.isRunning()
                    && gameManager.getActiveProcesses().size() == candidate.orders.size()
                    && basketContentsBeforeRestore.equals(basketManager.getContentsSnapshotForTest());
            loaded = true;
            if (candidate.terminal) gameManager.restoreTerminalResult(candidate.score, candidate.deadProcessCount);
            activityContent.setVisibility(VISIBLE);
        } catch (Exception e) {
            Log.w(TAG, "Saved run is corrupt and cannot be restored", e);
        } finally {
            loadSucceededForTest = loaded;
            if (loaded && getIntent().getBooleanExtra("pauseAfterLoadForTest", false)) {
                gameManager.pauseGame();
            }
            if (loaded && !gameManager.isGameOver()) gameManager.setLoading(false);
        }
        if (!loaded) rejectLoadedSave(getString(R.string.corrupt_save_message));
        else if (!gameManager.isGameOver()) Toast.makeText(this, "Game loaded", Toast.LENGTH_LONG).show();
    }

    private void rejectLoadedSave(String message) {
        loadSucceededForTest = false;
        if (gameManager != null) gameManager.setLoading(true);
        recoveryDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.save_recovery_title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    PrefsHelper.clearSaveState();
                    Intent intent = new Intent(GameActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private FoodItem createSavedItem(GameSaveSnapshot.SavedItem saved) {
        if (saved.type == PlayerInventory.INGREDIENT) return new Ingredient(saved.id);
        List<Ingredient> madeWith = new ArrayList<>();
        for (int id : saved.ingredients) madeWith.add(new Ingredient(id));
        return new CookedFood(saved.id, saved.name, madeWith);
    }

    private Recipe findRecipe(String name) {
        for (Recipe recipe : Recipe.getDefaultRecipes()) if (recipe.getName().equals(name)) return recipe;
        return null;
    }

    protected void initializeGameComponents() {
        sessionComposer.composeOnce(this::composeGameComponents);
    }

    protected boolean startsWithTutorialPause() { return false; }

    int getSessionCompositionCountForTest() { return sessionComposer.getCompositionCount(); }

    IngredientFetchWorker getIngredientFetcherForTest() { return ingredientFetcher; }
    PotThreadPool getPotThreadPoolForTest() { return potThreadPool; }
    GameView getGameViewForTest() { return findViewById(R.id.gameView); }
    int getDeliveredSessionCallbacksForTest() { return sessionCallbacks.deliveredCount(); }
    boolean loadSucceededForTest() { return loadSucceededForTest; }
    boolean restoreWasIsolatedForTest() { return restoreWasIsolatedForTest; }
    boolean isRecoveryDialogShowingForTest() { return recoveryDialog != null && recoveryDialog.isShowing(); }
    String recoveryMessageForTest() {
        if (recoveryDialog == null) return null;
        android.widget.TextView message = recoveryDialog.findViewById(android.R.id.message);
        return message == null ? null : message.getText().toString();
    }
    void acknowledgeRecoveryForTest() {
        if (isRecoveryDialogShowingForTest()) recoveryDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }
    boolean saveGameStateForTest() { return saveGameState(); }
    GameSaveSnapshot captureSnapshotForTest() {
        return GameSaveSnapshot.capture(game, gameManager, playerInventory);
    }

    Pot startCookForTest() {
        Pot pot = game.getPots().get(0);
        Recipe recipe = Recipe.getDefaultRecipes().get(0);
        for (Ingredient ingredient : recipe.getIngredients()) pot.getPotFunctions().addIngredient(ingredient);
        pot.getPotFunctions().beginCooking(recipe);
        pot.setState(Pot.State.COOKING.name());
        potThreadPool.submit(() -> {
            pot.getPotFunctions().cookIngredients(recipe, this);
            if (Thread.currentThread().isInterrupted()) return;
            try {
                while (!pauseState.runIfRunning(() -> pot.setState(Pot.State.DONE.name()))) {
                    if (!pauseState.awaitActiveDuration(0)) return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return pot;
    }

    void startFetchForTest() {
        List<Ingredient> used = ingredientFetcher.getUsedListForTest();
        List<Ingredient> available = ingredientFetcher.getAvailableList();
        ingredientFetcher.exchangeIngredient(used.get(0), available.get(0), this);
    }

    private void composeGameComponents() {
        if (startsWithTutorialPause()) pauseState.setPaused(PauseState.Reason.TUTORIAL, true);
        if (getIntent().getBooleanExtra("loadSavedGame", false)) pauseState.setPaused(PauseState.Reason.LOAD, true);
        GameView gameView = findViewById(R.id.gameView);

        List<Recipe> recipeList=Recipe.getDefaultRecipes();
        playerInventory = new PlayerInventory(recipeList);
        basketManager = new BasketManager(maxIngredients);
        int maxPots = 2;
        potThreadPool = new PotThreadPool(maxPots);
        game = new Game(gameView, this, playerInventory,potThreadPool,basketManager,this,pauseState);
        gameView.init(game);

        setupMovementControls();
        initializeUIComponents();
        initializeInventory();

        gameManager = new GameManager(this, this,recipeList,pauseState);
        game.setGameManager(gameManager);
        gameManager.startGame();

        updateScoreDisplay(0);
        updateDeadProcessCountDisplay(0);

        boolean shouldLoadSave = getIntent().getBooleanExtra("loadSavedGame", false);
        if (shouldLoadSave) {
            loadGameState();
        }
    }

    protected void setupMovementControls() {
        View btnUp = findViewById(R.id.btnUp);
        View btnDown = findViewById(R.id.btnDown);
        View btnLeft = findViewById(R.id.btnLeft);
        View btnRight = findViewById(R.id.btnRight);

        setupHoldMovement(btnUp, () -> game.moveUp());
        setupHoldMovement(btnDown, () -> game.moveDown());
        setupHoldMovement(btnLeft, () -> game.moveLeft());
        setupHoldMovement(btnRight, () -> game.moveRight());
    }

    private void setupHoldMovement(View button, Runnable movementAction) {
        button.setOnTouchListener((v, event) -> {
            int direction = v.getId();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.performClick();
                    if (heldMovementCallbacks.containsKey(v)) return true;
                    heldDirections.press(direction);
                    Runnable movementRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (!sessionCallbacks.isOpen() || !heldDirections.isHeld(direction) || !isMovementInputAllowed()) return;
                            movementAction.run();
                            if (heldDirections.isHeld(direction)) moveHandler.postDelayed(this, 150);
                        }
                    };
                    heldMovementCallbacks.put(v, movementRunnable);
                    movementRunnable.run();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Runnable callback = heldMovementCallbacks.remove(v);
                    if (callback != null) moveHandler.removeCallbacks(callback);
                    boolean anotherDirectionHeld = heldDirections.release(direction);
                    if (!anotherDirectionHeld && game != null && game.getPlayer() != null) game.getPlayer().stopMovement();
                    return true;
            }
            return false;
        });
    }

    protected void initializeUIComponents() {
        try {
            // Initialize statistics text views
            scoreTextView = findViewById(R.id.scoreTextView);
            deadProcessCountTextView = findViewById(R.id.deadProcessCountTextView);

            // Initialize process list
            RecyclerView processRecyclerView = findViewById(R.id.processRecyclerView);
            if (processRecyclerView == null) {
                Log.e(TAG, "processRecyclerView is null");
                return;
            }

            LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            processRecyclerView.setLayoutManager(layoutManager);
            orderAdapter = new OrderAdapter(this, new ArrayList<>());
            processRecyclerView.setAdapter(orderAdapter);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI components: " + e.getMessage(), e);
        }
    }

    protected void initializeInventory() {
        try {
            ingredientFetcher = new IngredientFetchWorker(maxIngredients,basketManager,pauseState);
            initializeViewLists();
            initIngredientViews();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ingredientInventory: " + e.getMessage(), e);
        }
    }

    private void initializeViewLists() {
        try {
            // Initialize ingredientInventory UI slots
            inventoryViews = new ArrayList<>();
            View slot1 = findViewById(R.id.ingredientSlot1);
            View slot2 = findViewById(R.id.ingredientSlot2);
            View slot3 = findViewById(R.id.ingredientSlot3);

            if (slot1 != null) inventoryViews.add((ImageView) slot1);
            if (slot2 != null) inventoryViews.add((ImageView) slot2);
            if (slot3 != null) inventoryViews.add((ImageView) slot3);

            // Initialize available ingredients UI view
            availableIngredientsViews = new ArrayList<>();
            View option1 = findViewById(R.id.swapOption1);
            View option2 = findViewById(R.id.swapOption2);

            if (option1 != null) availableIngredientsViews.add((ImageView) option1);
            if (option2 != null) availableIngredientsViews.add((ImageView) option2);

            // Get other UI elements
            swapOptionsLayout = findViewById(R.id.swapOptionsLayout);
            ingredientBlocker = findViewById(R.id.ingredientBlockerOverlay);
            playerInventoryView = findViewById(R.id.playerInventory);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing view lists: " + e.getMessage(), e);
        }
    }

    private void updateScoreDisplay(int score) {
        try {
            if (scoreTextView != null) {
                scoreTextView.setText(getString(R.string.score_format, score));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating score display: " + e.getMessage(), e);
        }
    }

    private void updateDeadProcessCountDisplay(int count) {
        try {
            if (deadProcessCountTextView != null) {
                deadProcessCountTextView.setText(getString(R.string.failed_processes_format, count));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating dead process count: " + e.getMessage(), e);
        }
    }

    private void initIngredientViews() {
        try {
            if (ingredientFetcher == null) {
                Log.e(TAG, "ingredientInventory or ingredientFetcher is null");
                return;
            }

            //Generate initial 3 ingredients randomly
            List<Ingredient> initialList = ingredientFetcher.generateIngredientsRandom(this);
            Log.d(TAG, "Initial ingredients: " + initialList.size());

            if (initialList.isEmpty()) {
                Log.e(TAG, "Initial ingredient list is empty");
                return;
            }

            //Set up the click listeners
            setupInventoryClickListeners();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ingredient views: " + e.getMessage(), e);
        }
    }

    private void updatePlayerInventoryView(){
        if (playerInventory.checkHeldType()!= PlayerInventory.EMPTY){
            playerInventoryView.setImageResource(playerInventory.getHeld().getIconResourceId());
        }else{
            playerInventoryView.setImageResource(R.drawable.button_secondary);
        }
    }

    private void updateAvailableUI() {
        //Update the available Ingredient views in UI
        try {
            if (ingredientFetcher == null || availableIngredientsViews == null || availableIngredientsViews.isEmpty()) {
                Log.e(TAG, "ingredientFetcher or availableIngredientsViews is null/empty");
                return;
            }

            //Get list of available ingredients
            List<Ingredient> availableIngredients = ingredientFetcher.getAvailableList();
            if (availableIngredients == null || availableIngredients.isEmpty()) {
                Log.e(TAG, "Swappable ingredients list is null/empty");
                return;
            }

            for (int i = 0; i < availableIngredientsViews.size(); i++) {
                if (i >= availableIngredients.size()) {
                    break;
                }

                final int index = i;
                ImageView avIngView = availableIngredientsViews.get(i);
                Ingredient ingredient = availableIngredients.get(i);

                if (ingredient != null) {
                    //Set the image and background resources
                    avIngView.setImageResource(ingredient.getIconResourceId());
                    avIngView.setBackgroundResource(R.drawable.swap_options_normal);

                    //Set the onClickListener for each available view
                    avIngView.setOnClickListener(v -> handleAvailableIngredientClick(index));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating available UI: " + e.getMessage(), e);
        }
    }

    private void handleAvailableIngredientClick(int index) {
        if (!isGameplayInputAllowed()) return;
        //Onclick handler
        try {
            if (selectedIngredientIndex != -1 && ingredientFetcher != null) {
                //Get available list of swap ingredients
                List<Ingredient> availableList = ingredientFetcher.getAvailableList();

                if (availableList != null && index < availableList.size() && selectedIngredientIndex < basketManager.getBasketCount()) {
                    //map the basket index to selectedIngredientIndex
                    int basketIndex=maxIngredients-1-selectedIngredientIndex;//basket ingredients are reverse of used list ingredients
                    Ingredient dropIngredient = basketManager.getIngredientFromBasket(basketIndex);

                    availableIngredientsViews.get(index).setBackgroundResource(R.drawable.swap_options_selected);

                    selectedSwapIndex = index;

                    //Disable the views until swap is done
                    disableAllViews();

                    //Put up the blocker in case disable fails
                    if (ingredientBlocker != null) {
                        ingredientBlocker.setVisibility(VISIBLE);
                    }

                    try{
                        Log.d(TAG,"exchanging "+dropIngredient.getName()+" for "+availableList.get(index).getName());
                        ingredientFetcher.exchangeIngredient(dropIngredient, availableList.get(index), this);
                    }catch(Exception e){
                        Log.e(TAG,"Failed to fetch ingredient:"+e.getLocalizedMessage());
                    }

                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling available ingredient click: " + e.getMessage(), e);
            resetSelectionState();
        }
    }

    private void disableAllViews() {
        //Disable all ingredient views when swapping
        try {
            disableAll(availableIngredientsViews);
            disableAll(inventoryViews);
        } catch (Exception e) {
            Log.e(TAG, "Error disabling views: " + e.getMessage(), e);
        }
    }

    private void updateInventoryUI(List<Ingredient> invHeld) {
        //Update inventory icons
        try {
            updateInventoryIcons(invHeld);

            Log.d(TAG, "Finished updateInventoryUI()");
        } catch (Exception e) {
            Log.e(TAG, "Error in updateInventoryUI: " + e.getMessage(), e);
        }
    }

    private void updateInventoryIcons(List<Ingredient> invHeld) {
        //Update inventory icons on every basket update to match baskets
        try {
            if (inventoryViews == null || invHeld == null) {
                return;
            }

            for (int i = 0; i < inventoryViews.size(); i++) {
                if (i >= invHeld.size()) {
                    break;
                }

                ImageView view = inventoryViews.get(i);
                if (view != null) {
                    view.setImageResource(invHeld.get(i).getIconResourceId());
                    view.setBackgroundResource(R.drawable.inventory_slot_normal);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating ingredientInventory icons: " + e.getMessage(), e);
        }
    }

    private void setupInventoryClickListeners() {
        //Set up the inventory click listeners
        try {
            if (inventoryViews == null) {
                return;
            }

            for (int i = 0; i < inventoryViews.size(); i++) {

                final int index = i;
                ImageView invView = inventoryViews.get(i);

                if (invView == null) continue;

                invView.setOnClickListener(v -> handleInventoryItemClick(index));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up ingredientInventory click listeners: " + e.getMessage(), e);
        }
    }

    private void handleInventoryItemClick(int index) {
        if (!isGameplayInputAllowed()) return;
        //Inventory click handler, set selectedIngredientIndex and appropriate styling
        try {
            Log.d(TAG, "Clicked ingredientInventory index: " + index);

            if (selectedIngredientIndex == index) {//If click on already selected inventory
                // Deselect the current item
                selectedIngredientIndex = -1;

                //Make available list invisible
                if (swapOptionsLayout != null) {
                    swapOptionsLayout.setVisibility(INVISIBLE);
                }
                disableAll(availableIngredientsViews);

                //Set the background of inventory views to unselected
                if (index < inventoryViews.size()) {
                    inventoryViews.get(index).setBackgroundResource(R.drawable.inventory_slot_normal);
                }


            } else {
                // Deselect previous selection if there was one
                if (selectedIngredientIndex != -1 && selectedIngredientIndex < inventoryViews.size()) {
                    inventoryViews.get(selectedIngredientIndex).setBackgroundResource(R.drawable.inventory_slot_normal);
                }

                // Select new item
                selectedIngredientIndex = index;

                if (index < inventoryViews.size()) {
                    inventoryViews.get(index).setBackgroundResource(R.drawable.inventory_slot_selected);
                }

                //Make available ingredients visible and enable them
                if (swapOptionsLayout != null) {
                    swapOptionsLayout.setVisibility(VISIBLE);
                }
                enableAll(availableIngredientsViews);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling ingredientInventory item click: " + e.getMessage(), e);
            resetSelectionState();
        }
    }

    private void enableAll(List<? extends View> views) {
        try {
            if (views == null) return;

            for (View v : views) {
                if (v != null) {
                    v.setEnabled(true);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling views: " + e.getMessage(), e);
        }
    }

    private void disableAll(List<? extends View> views) {
        try {
            if (views == null) return;

            for (View v : views) {
                if (v != null) {
                    v.setEnabled(false);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error disabling views: " + e.getMessage(), e);
        }
    }

    @Override
    public void fetchIngredientProgressUpdate(int progress){
        //Add progress updater for ingredient fetch
        postRunningSessionUi(() -> Log.d(TAG,"Received progress update for ingredient:"+progress));
    }

    @Override
    public void finishedBasketFilling(List<Ingredient> fillOrder){
        //Listener to update ui when baskets are changed
        postRunningSessionUi(() -> {
            try {
                updateAvailableUI();
                updateInventoryUI(fillOrder);

                // Reset selection states
                resetSelectionState();

                // Re-enable ingredientInventory items
                enableAll(inventoryViews);

                // Hide UI elements
                if (ingredientBlocker != null) {
                    ingredientBlocker.setVisibility(GONE);
                }

                if (swapOptionsLayout != null) {
                        swapOptionsLayout.setVisibility(INVISIBLE);
                }

                } catch (Exception e) {
                    Log.e(TAG, "Error in finish basket filling: " + e.getMessage(), e);
                }
            });
    }

    @Override
    public void potProgressUpdate(int progress){
        //Add progress updates for pot
        postRunningSessionUi(() -> Log.d(TAG,"Received progress update for pot:"+progress));
    }

    private void resetSelectionState() {
        try {
            // Reset background for selected ingredientInventory item if valid
            if (selectedIngredientIndex >= 0 && selectedIngredientIndex < inventoryViews.size()) {
                inventoryViews.get(selectedIngredientIndex).setBackgroundResource(R.drawable.inventory_slot_normal);
            }

            // Reset background for selected swap option if valid
            if (selectedSwapIndex >= 0 && selectedSwapIndex < availableIngredientsViews.size()) {
                availableIngredientsViews.get(selectedSwapIndex).setBackgroundResource(R.drawable.swap_options_normal);
            }

            // Reset selection indices
            selectedSwapIndex = -1;
            selectedIngredientIndex = -1;

            // Re-enable all views
            enableAll(inventoryViews);

            // Hide UI elements
            if (ingredientBlocker != null) {
                ingredientBlocker.setVisibility(GONE);
            }

            if (swapOptionsLayout != null) {
                swapOptionsLayout.setVisibility(INVISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resetting selection state: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onPause() {
        cancelHeldMovement();
        super.onPause();
        try {
            if (gameManager != null) gameManager.pauseForBackground();

            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            hideSystemUI();

            if (gameManager != null) gameManager.resumeFromBackground();
            refreshPauseUi();

            if (mediaPlayer != null) {
                mediaPlayer.start();
            }
            applyJoystickScale(findViewById(android.R.id.content));

        } catch (Exception e) {
            Log.e(TAG, "Error in onResume: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onStop() {
        cancelHeldMovement();
        super.onStop();
    }

    protected boolean isMovementInputAllowed() { return pauseState.isMovementAllowed(); }

    protected boolean isGameplayInputAllowed() { return gameManager != null && gameManager.isRunning(); }

    @Override
    protected void onDestroy() {
        sessionCallbacks.close();
        cancelHeldMovement();
        super.onDestroy();
        try {
            if (gameManager != null) {
                gameManager.stopGame();
            }
            if (ingredientFetcher != null) ingredientFetcher.close();
            if (potThreadPool != null) potThreadPool.close();
            GameView gameView = findViewById(R.id.gameView);
            if (gameView != null) gameView.shutdown();

            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy: " + e.getMessage(), e);
        }
    }


    private void updateMediaPlaybackSpeed(int deadProcessCount) {
        if (deadProcessCount >= 3) {
            if (mediaPlayer != null) {
                mediaPlayer.stop(); // Stop the music
                mediaPlayer.reset(); // Reset the MediaPlayer to prepare for reuse if needed
                mediaPlayer = MediaPlayer.create(this, R.raw.gameover);
//                sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
//                int savedVolume = sharedPreferences.getInt("volume", 100);
                int savedVolume = PrefsHelper.getVolume();
                float volume = savedVolume / 100f;
                mediaPlayer.setVolume(volume,volume);
                mediaPlayer.start(); // Start playing the audio
            }
            return;
        }

        float speed;
        switch (deadProcessCount) {
            case 0:
                speed = 1.0f; // 0/3 speed
                break;
            case 1:
                speed = 2.0f; // 1/3 speed
                break;
            case 2:
                speed = 3.0f; // 2/3 speed
                break;
            default:
                speed = 1.0f; // Fallback (should not occur)
                break;
        }

        if (mediaPlayer != null) {
            PlaybackParams playbackParams = new PlaybackParams();
            playbackParams.setSpeed(speed);
            mediaPlayer.setPlaybackParams(playbackParams);
            Log.d(TAG, "Playback speed updated to: " + speed);
        }
    }

    // GameManager.GameListener implementation
    @Override
    public void onTimerTick() {
        postSessionUi(() -> {
            try {
                if (gameManager != null && orderAdapter != null) {
                    updatePlayerInventoryView();
                    orderAdapter.updateProcesses(gameManager.getActiveProcesses());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onTimerTick: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void onProcessAdded(Order order) {
        postSessionUi(() -> {
            try {
                if (orderAdapter != null && gameManager != null) {
                    orderAdapter.updateProcesses(gameManager.getActiveProcesses());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onProcessAdded: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void onProcessCompleted(Order order) {
        postSessionUi(() -> {
            try {
                if (orderAdapter != null && gameManager != null) {
                    orderAdapter.updateProcesses(gameManager.getActiveProcesses());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onProcessCompleted: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void onProcessDied(Order order) {
        postSessionUi(() -> {
            if (orderAdapter != null && gameManager != null) {
                orderAdapter.updateProcesses(gameManager.getActiveProcesses());
                updateDeadProcessCountDisplay(gameManager.getDeadProcessCount());
                updateMediaPlaybackSpeed(gameManager.getDeadProcessCount());
            }
        });
    }

    @Override
    public void onScoreChanged(int newScore) {
        postSessionUi(() -> {
            try {
                updateScoreDisplay(newScore);
            } catch (Exception e) {
                Log.e(TAG, "Error in onScoreChanged: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public void onGameOver(int finalScore) {
        postSessionUi(() -> {
            gameOverFinalization.runOnce(() -> {
              cancelHeldMovement();
              View pauseMenu = findViewById(R.id.pauseMenu);
              if (pauseMenu != null) pauseMenu.setVisibility(View.GONE);
              View togglePause = findViewById(R.id.togglePauseButton);
              if (togglePause != null) togglePause.setEnabled(false);
              GameOverPersistence.complete(finalScore, new GameOverPersistence.StatsStore() {
                @Override public int getHighScore() { return PrefsHelper.getHighScore(); }
                @Override public int getGamesPlayed() { return PrefsHelper.getGamesPlayed(); }
                @Override public float getAverageScore() { return PrefsHelper.getAverageScore(); }
                @Override public void setHighScore(int score) { PrefsHelper.setHighScore(score); }
                @Override public void setAverageScore(float score) { PrefsHelper.setAverageScore(score); }
                @Override public void setGamesPlayed(int count) { PrefsHelper.setGamesPlayed(count); }
              }, PrefsHelper::clearSaveState);
              GameOverDialog gameOverDialog = new GameOverDialog(this, finalScore);
              gameOverDialog.show();
            });
        });
    }

    private void cancelHeldMovement() {
        for (Runnable callback : heldMovementCallbacks.values()) moveHandler.removeCallbacks(callback);
        heldMovementCallbacks.clear();
        heldDirections.clear();
        if (game != null && game.getPlayer() != null) game.getPlayer().stopMovement();
    }

    private void refreshPauseUi() {
        if (gameManager == null) return;
        View pauseMenu = findViewById(R.id.pauseMenu);
        if (pauseMenu != null) {
            if (gameManager.isGameOver()) pauseMenu.setVisibility(View.GONE);
            else if (!pauseState.isPaused(PauseState.Reason.TUTORIAL)) {
                View settingsMenu = findViewById(R.id.SettingsMenu);
                boolean settingsOpen = settingsMenu != null && settingsMenu.getVisibility() == View.VISIBLE;
                pauseMenu.setVisibility(gameManager.isRunning() || settingsOpen ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void postSessionUi(Runnable callback) {
        if (!sessionCallbacks.isOpen()) return;
        runOnUiThread(() -> sessionCallbacks.runIfOpen(callback));
    }

    private void postRunningSessionUi(Runnable callback) {
        if (!sessionCallbacks.isOpen()) return;
        Runnable[] deliver = new Runnable[1];
        deliver[0] = () -> {
            if (!sessionCallbacks.isOpen() || pauseState.isTerminal()) return;
            if (pauseState.isRunning()) sessionCallbacks.runIfOpen(callback);
            else moveHandler.postDelayed(deliver[0], 50);
        };
        runOnUiThread(deliver[0]);
    }
}
