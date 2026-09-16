# Testing Checklist - USBrain Reorganization

## Pre-Test Setup
- ✅ Dev Server running: http://localhost:8081
- Open browser at http://localhost:8081/index.html

## Test Cases

### 1. Login Flow
- [ ] App loads without errors
- [ ] Can see login screen
- [ ] Can toggle between roles (Terapeuta/Paciente)
- [ ] 2FA option appears correctly

### 2. Therapist Workflow
- [ ] Login as Terapeuta (Jose)
- [ ] See Therapist Home screen with cases/patients
- [ ] Click "Nueva conducta" button
- [ ] Form appears with fields:
  - Nombre de conducta
  - Definición operacional
  - Clasificación (Adaptativa/Desadaptativa)
  - Dimensión (Frecuencia/Duración/Intensidad)
- [ ] Create adaptiva conduct (test name: "Participar en clase")
- [ ] Create desadaptiva conduct (test name: "Evitar participar")
- [ ] Both appear in Therapist Patient screen

### 3. Baseline Management
- [ ] Select a conduct
- [ ] Baseline shows as "Abierta" (open)
- [ ] Can close baseline by clicking "Cerrar línea base"
- [ ] Baseline state changes to "Cerrada"

### 4. Task Assignment (After Baseline Closed)
- [ ] Phase auto-changes to INTERVENCION
- [ ] Can create new task for conduct
- [ ] Task shows correct phase (INTERVENCION)

### 5. Preview as Patient
- [ ] Click "Previsualizar" as therapist
- [ ] Switch to patient view
- [ ] See "Mi progreso" screen

### 6. Patient Progress Screen
- [ ] See header "Mi progreso"
- [ ] Conducts grouped by classification:
  - Section: "Conductas adaptativas" (if any exist)
  - Section: "Conductas desadaptativas" (if any exist)
- [ ] Each conduct shows:
  - Name
  - Type (Adaptativa/Desadaptativa)
  - Dimension
  - Chart with baseline and intervention data

### 7. Patient Task Management
- [ ] Patient Home shows list of pending tasks
- [ ] Click on task to open
- [ ] Task screen shows:
  - Task title
  - Therapist instructions
  - Input widget (Stepper/TextField/Slider based on dimension)
  - Save button
- [ ] Save task entry
- [ ] Redirects to "Registro guardado" screen
- [ ] Entry appears in progress chart

### 8. Navigation & Theme
- [ ] Bottom navigation works
- [ ] Theme toggle (light/dark) works
- [ ] "Reiniciar demo" resets data
- [ ] No console errors

### 9. Data Integrity
- [ ] Switching between roles maintains data
- [ ] Charts show correct data after entries
- [ ] Baseline entries separate from intervention entries in chart

## Known Limitations (Accept as-is for MVP)
- No backend persistence
- localStorage sync optional
- Demo data only
- No actual therapist assignment
