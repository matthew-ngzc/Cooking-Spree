package com.game.cookingspree;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TutorialActivity extends GameActivity {

    private LinearLayout tutorialOverlay;
    private TextView tutorialText;
    private Button tutorialNextButton;

    private int currentStep = 0;
    private List<TutorialStep> tutorialSteps;
    private boolean tutorialComplete;

    @Override
    protected int getGameLayoutResource() { return R.layout.activity_tutorial; }

    @Override protected boolean startsWithTutorialPause() { return true; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Initialize tutorial UI
        initializeTutorial();
    }

    private void initializeTutorial() {
        // Get tutorial overlay views with null checks
        tutorialOverlay = findViewById(R.id.tutorialOverlay);
        tutorialText = findViewById(R.id.tutorialText);
        tutorialNextButton = findViewById(R.id.tutorialNextButton);
        Button tutorialSkipButton = findViewById(R.id.tutorialSkipButton);

        if (tutorialOverlay == null || tutorialText == null ||
                tutorialNextButton == null || tutorialSkipButton == null) {
            Log.e("TutorialActivity", "Tutorial UI elements not found!");
            return;
        }

        // Setup tutorial steps
        setupTutorialSteps();

        // Setup button listeners
        tutorialNextButton.setOnClickListener(v -> nextStep());
        tutorialSkipButton.setOnClickListener(v -> skipTutorial());

        // Start tutorial
        showCurrentStep();

    }

    // Helper method to access gameManager through game instance
    private GameManager getTutorialGameManager() {
        // Access gameManager through the game instance
        if (game != null && game.getGameManager() != null) {
            return game.getGameManager();
        }
        return null;
    }

    private void setupTutorialSteps() {
        tutorialSteps = new ArrayList<>(Arrays.asList(
                new TutorialStep(
                        "Welcome to Cooking Spree!",
                        "This is your kitchen. Let's learn the basics!"
                ),
                new TutorialStep(
                        "Movement",
                        "Use the joystick on the bottom left to move around. Try moving your character!"
                ),
                new TutorialStep(
                        "Orders",
                        "Orders appear at the top. They show what recipe to cook and time remaining. More orders = more points!"
                ),
                new TutorialStep(
                        "Ingredients",
                        "On the right are your available ingredients. Click one to select it, then click a swap option to exchange ingredients."
                ),
                new TutorialStep(
                        "Baskets",
                        "Walk to a basket and press Interact to pick up ingredients. You can only hold one item at a time."
                ),
                new TutorialStep(
                        "Cooking Pots",
                        "Add 3 ingredients to a pot to start cooking. Make sure they match a recipe! Wrong ingredients = waste."
                ),
                new TutorialStep(
                        "Tables",
                        "Use tables to store items temporarily. Press Interact to place or pick up items."
                ),
                new TutorialStep(
                        "Submission Zone",
                        "Bring completed dishes here to submit orders and score points. Wrong dish = no points!"
                ),
                new TutorialStep(
                        "Trash Bin",
                        "Made a mistake? Throw unwanted items in the trash bin."
                ),
                new TutorialStep(
                        "Ready to Cook!",
                        "You now know the basics! Cook fast, submit orders, and don't let them expire. Good luck, Chef!"
                )
        ));
    }

    private void showCurrentStep() {
        if (currentStep >= tutorialSteps.size()) {
            completeTutorial();
            return;
        }

        TutorialStep step = tutorialSteps.get(currentStep);
        tutorialText.setText(step.getDescription());
        tutorialOverlay.setVisibility(View.VISIBLE);

        // Update button text for last step
        if (currentStep == tutorialSteps.size() - 1) {
            tutorialNextButton.setText("Start Game!");
        }

        // Special handling for certain steps
        handleSpecialSteps();
    }

    private void handleSpecialSteps() {
        switch (currentStep) {
            case 1: // Movement step
                pauseState.allowTutorialMovement(true);
                break;
            case 2: // Orders step
                pauseState.allowTutorialMovement(false);
                break;
            default:
                pauseState.allowTutorialMovement(false);
                break;
        }
    }

    private void nextStep() {
        currentStep++;
        showCurrentStep();
    }

    private void skipTutorial() {
        completeTutorial();
    }

    private void completeTutorial() {
        if (tutorialComplete) return;
        tutorialComplete = true;
        tutorialOverlay.setVisibility(View.GONE);
        pauseState.allowTutorialMovement(false);
        gameManager.pauseForTutorial(false);
        Button togglePauseButton = findViewById(R.id.togglePauseButton);
        if (togglePauseButton != null) togglePauseButton.setVisibility(View.VISIBLE);

        // Optionally go back to main menu or continue playing
        // For now, let's continue in tutorial mode
    }

    // Simple tutorial step class
    private static class TutorialStep {
        private final String description;

        public TutorialStep(String title, String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Override to disable pause menu during tutorial
    @Override
    protected void setupPauseMenuButtons() {
        try {
            super.setupPauseMenuButtons();

            // Disable pause button during tutorial
            Button togglePauseButton = findViewById(R.id.togglePauseButton);
            if (togglePauseButton != null) {
                togglePauseButton.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e("TutorialActivity", "Error setting up pause menu: " + e.getMessage(), e);
        }
    }
}
