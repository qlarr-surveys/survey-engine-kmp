# Qlarr Survey Engine
Qlarr Survey Engine is a UI-agnostic tool that lets you create and run customizable, scientific & offline-first **surveys as code** on all platforms. Surveys are defined using JSON to represent structure and [Javascript](https://github.com/qlarr-surveys/survey-engine-script) instructions to represent survey logic. Qlarr Survey Engine parses the survey definition and into a **JavaScript State Machine** that reacts to user interaction to manage and update the survey state.

<img width="500" alt="Screenshot 2024-10-31 at 21 23 52" src="https://github.com/user-attachments/assets/f4d30698-2f1f-4628-9299-240da0ab221e">

### Qlarr Survey Engine Supports
1. 📴 Offline Surveys
2. ⍰ Conditional Logic and Skip Logic
3. ✅ Input Validation
4. 🎲 Randomization and Sampling
5. 🌐 Multilingual Surveys
6. 🔗 Piping values from users' previous answers
7. ⬅️➡️ Flexible Navigation: all questions, page by page or question by question
8. 🎨 Conditional Formatting
9. **WIP**: ⏱️ Time limits and 📊 Scoring for Quizzes


## Usecases
Qlarr Survey Engine has two main usecases


### Process Survey (during survey design)
Qlarr Survey Engine takes the survey design as input and generates the following:
1. **Component Graph**: Survey components organized into nodes linked by parent-child and sibling relationships, ensuring scope validation and logical consistency.
2. **State Machine Files**: Resources to build a state machine that runs on UI to react to user interaction and update the survey state.
3. **DB Schema**: The schema to generate a relational database to save the survey responses
4. **Augmented/Validated Design**: The survey design file with structure and logic validated and with additional instructions that supports navigation
<img width="722" alt="Screenshot 2024-10-31 at 21 23 52" src="https://github.com/user-attachments/assets/0ad07008-c1bc-4f23-a95c-7af3761437b7">


### Navigate Survey (during survey execution)
takes processed survey and existing user responses (if any) and generates:
1. **Reduced Survey**: Survey structure reduced to include the relevant content according to the user current location in the survey
2. **Survey State**: Resources to build a state machine that runs on UI to react to user interaction and update the survey state.
3. **Values to Save**: the user responses to save to the database
<img width="723" alt="Screenshot 2024-10-31 at 21 24 13" src="https://github.com/user-attachments/assets/2394a22a-2525-4ef2-bb41-1aaacbc69a92">

## State of Development
This library is built with **Kotlin Multiplatform (KMP)** and supports multiple platforms:
- **JVM**: For Spring Boot backends and JVM-based applications
- **JavaScript**: For Node.js backends and browser-based applications
- **iOS**: Native iOS support via CocoaPods framework

The library is currently at version 0.1.6 and published as `com.qlarr.survey-engine:surveyengine`.
