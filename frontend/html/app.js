const MUSCLE_GROUPS = {
    CHEST: 'Грудь',
    BACK: 'Спина',
    LEGS: 'Ноги',
    SHOULDERS: 'Плечи',
    ARMS: 'Руки',
    CORE: 'Пресс',
    CARDIO: 'Кардио'
};

const MONTHS = ['янв', 'фев', 'мар', 'апр', 'май', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];
const WORKOUT_FORMS = {one: 'тренировка', few: 'тренировки', many: 'тренировок'};
const EXERCISE_FORMS = {one: 'упражнение', few: 'упражнения', many: 'упражнений'};
const REP_FORMS = {one: 'повторение', few: 'повторения', many: 'повторений'};

const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'];

const pluralRules = new Intl.PluralRules('ru-RU');
const weekdayFormat = new Intl.DateTimeFormat('ru-RU', {weekday: 'long'});
const dateFormat = new Intl.DateTimeFormat('ru-RU');
const monthFormat = new Intl.DateTimeFormat('ru-RU', {month: 'long'});
const fullDateFormat = new Intl.DateTimeFormat('ru-RU', {day: 'numeric', month: 'long', year: 'numeric'});
const numberFormat = new Intl.NumberFormat('ru-RU', {maximumFractionDigits: 2});

const toast = document.getElementById('toast');
const workoutDialog = document.getElementById('workout-dialog');
const exerciseDialog = document.getElementById('exercise-dialog');
const confirmDialog = document.getElementById('confirm-dialog');
const setForm = document.getElementById('set-form');
const setSubmit = document.getElementById('set-submit');
const setCancel = document.getElementById('set-cancel');
const exerciseSearch = document.getElementById('exercise-search');
const muscleFilter = document.getElementById('muscle-filter');
const authView = document.getElementById('view-auth');
const authForm = document.getElementById('auth-form');
const authToggle = document.getElementById('auth-toggle');
const logoutButton = document.getElementById('logout');

const workoutsMore = document.getElementById('workouts-more');

const PAGE_SIZE = 20;

let exercises = [];
let workouts = [];
let workoutsPage = 0;
let registering = false;
let currentWorkout = null;
let selectedGroup = '';
let toastTimer;

async function api(path, options = {}) {
    let response;
    try {
        response = await fetch(`/api${path}`, {
            method: options.method ?? 'GET',
            headers: {'Content-Type': 'application/json'},
            body: options.body && JSON.stringify(options.body)
        });
    } catch {
        throw new Error('Нет соединения с сервером');
    }
    if (response.status === 401 && !path.startsWith('/auth/')) {
        showAuth();
        throw new Error('Войдите в аккаунт');
    }
    if (!response.ok) {
        const problem = await response.json().catch(() => ({}));
        throw new Error(problem.detail ?? `Ошибка ${response.status}`);
    }
    return response.status === 204 ? null : response.json();
}

async function run(action) {
    try {
        await action();
    } catch (e) {
        showToast(e.message);
    }
}

function showToast(message) {
    toast.textContent = message;
    toast.classList.add('visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('visible'), 4000);
}

function el(tag, props = {}, ...children) {
    const element = Object.assign(document.createElement(tag), props);
    element.append(...children.filter(Boolean));
    return element;
}

function icon(name) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
    use.setAttribute('href', `#icon-${name}`);
    svg.classList.add('icon');
    svg.append(use);
    return svg;
}

function iconButton(name, label, handler, className = '') {
    const button = el('button', {type: 'button', className: `icon-button ${className}`, title: label}, icon(name));
    button.setAttribute('aria-label', label);
    button.addEventListener('click', event => {
        event.preventDefault();
        run(handler);
    });
    return button;
}

function rowActions(onEdit, onDelete) {
    return el('span', {className: 'row-actions'},
        iconButton('edit', 'Изменить', onEdit),
        iconButton('trash', 'Удалить', onDelete, 'danger'));
}

function placePopup(anchor, popup) {
    const rect = anchor.getBoundingClientRect();
    const below = innerHeight - rect.bottom - 12;
    const above = rect.top - 12;
    const up = popup.scrollHeight > below && above > below;
    Object.assign(popup.style, {
        left: `${Math.max(8, Math.min(rect.left, innerWidth - popup.offsetWidth - 8))}px`,
        top: up ? 'auto' : `${rect.bottom + 6}px`,
        bottom: up ? `${innerHeight - rect.top + 6}px` : 'auto',
        maxHeight: `${Math.min(360, up ? above : below)}px`
    });
}

function dismissOnOutside(root, popup, close) {
    document.addEventListener('pointerdown', event => {
        if (!root.contains(event.target)) close();
    });
    document.addEventListener('scroll', event => {
        if (!popup.contains(event.target)) close();
    }, true);
    addEventListener('resize', close);
}

function enhanceSelect(select) {
    const label = el('span', {className: 'picker-label'});
    const trigger = el('button', {type: 'button', className: 'picker-trigger'}, label, icon('select'));
    const menu = el('div', {className: 'select-menu', role: 'listbox', popover: 'manual'});
    const root = el('div', {className: 'picker select'}, trigger, menu);
    const options = [];

    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');
    trigger.setAttribute('aria-label', select.getAttribute('aria-label') ?? '');
    select.before(root);
    root.append(select);
    select.className = 'picker-native';
    select.tabIndex = -1;

    const isOpen = () => menu.matches(':popover-open');

    function sync() {
        const selected = select.selectedOptions[0];
        label.textContent = selected?.textContent ?? '';
        label.classList.toggle('placeholder', !selected || selected.disabled);
        for (const item of options) item.setAttribute('aria-selected', item.dataset.value === select.value);
    }

    function build() {
        const item = option => {
            if (option.disabled) return null;
            const element = el('div', {className: 'select-option', role: 'option', tabIndex: -1, textContent: option.textContent});
            element.dataset.value = option.value;
            options.push(element);
            return element;
        };
        options.length = 0;
        menu.replaceChildren(...[...select.children].flatMap(child => child.tagName === 'OPTGROUP'
            ? [el('div', {className: 'select-group', role: 'presentation', textContent: child.label}),
                ...[...child.children].map(item)]
            : item(child)).filter(Boolean));
        sync();
    }

    function open() {
        menu.showPopover();
        trigger.setAttribute('aria-expanded', 'true');
        menu.style.width = `${trigger.offsetWidth}px`;
        placePopup(trigger, menu);
        const current = options.find(item => item.dataset.value === select.value) ?? options[0];
        current?.focus();
        current?.scrollIntoView({block: 'nearest'});
    }

    function close(refocus = true) {
        if (!isOpen()) return;
        menu.hidePopover();
        trigger.setAttribute('aria-expanded', 'false');
        if (refocus) trigger.focus();
    }

    function choose(value) {
        select.value = value;
        select.dispatchEvent(new Event('change', {bubbles: true}));
        close();
    }

    trigger.addEventListener('click', () => isOpen() ? close() : open());
    trigger.addEventListener('keydown', event => {
        if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
        event.preventDefault();
        open();
    });

    menu.addEventListener('click', event => {
        event.preventDefault();
        const item = event.target.closest('.select-option');
        if (item) choose(item.dataset.value);
    });
    menu.addEventListener('mouseover', event => event.target.closest('.select-option')?.focus());
    menu.addEventListener('keydown', event => {
        const index = options.indexOf(document.activeElement);
        const target = {ArrowDown: index + 1, ArrowUp: index - 1, Home: 0, End: options.length - 1}[event.key];
        if (event.key === 'Tab') return close();
        if (target !== undefined) {
            options[Math.min(Math.max(target, 0), options.length - 1)].focus();
        } else if (event.key === 'Enter' || event.key === ' ') {
            if (index >= 0) choose(options[index].dataset.value);
        } else if (event.key === 'Escape') {
            close();
        } else {
            return;
        }
        event.preventDefault();
    });

    dismissOnOutside(root, menu, () => close(false));

    select.addEventListener('change', sync);
    select.form.addEventListener('reset', () => setTimeout(sync));
    new MutationObserver(build).observe(select, {childList: true, subtree: true});
    build();
}

function enhanceDate(input) {
    const label = el('span', {className: 'picker-label'});
    const trigger = el('button', {type: 'button', className: 'picker-trigger'}, label, icon('calendar'));
    const title = el('span', {className: 'calendar-title'});
    const grid = el('div', {className: 'calendar-grid'});
    const todayButton = el('button', {type: 'button', className: 'calendar-today', textContent: 'Сегодня'});
    const calendar = el('div', {className: 'calendar', popover: 'manual'},
        el('div', {className: 'calendar-head'},
            title,
            el('div', {className: 'calendar-nav'},
                iconButton('prev', 'Предыдущий месяц', () => shiftMonth(-1)),
                iconButton('chevron', 'Следующий месяц', () => shiftMonth(1)))),
        el('div', {className: 'calendar-weekdays'}, ...WEEKDAYS.map(day => el('span', {textContent: day}))),
        grid,
        el('div', {className: 'calendar-foot'}, todayButton));
    const root = el('div', {className: 'picker'}, trigger, calendar);
    let focus = new Date();

    trigger.setAttribute('aria-haspopup', 'dialog');
    trigger.setAttribute('aria-expanded', 'false');
    trigger.setAttribute('aria-label', input.getAttribute('aria-label') ?? '');
    input.before(root);
    root.append(input);
    input.className = 'picker-native';
    input.tabIndex = -1;

    const isOpen = () => calendar.matches(':popover-open');

    function sync() {
        label.textContent = input.value ? dateFormat.format(parseDate(input.value)) : 'Выберите дату';
        label.classList.toggle('placeholder', !input.value);
    }

    function render() {
        const year = focus.getFullYear();
        const month = focus.getMonth();
        const start = new Date(year, month, 1 - (new Date(year, month, 1).getDay() + 6) % 7);
        title.textContent = `${capitalize(monthFormat.format(focus))} ${year}`;
        grid.replaceChildren(...Array.from({length: 42}, (_, i) => {
            const day = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
            const iso = toIso(day);
            const button = el('button', {
                type: 'button',
                className: 'calendar-day',
                textContent: day.getDate(),
                tabIndex: iso === toIso(focus) ? 0 : -1
            });
            button.dataset.date = iso;
            button.classList.toggle('outside', day.getMonth() !== month);
            button.classList.toggle('today', iso === today());
            button.classList.toggle('selected', iso === input.value);
            return button;
        }));
    }

    function focusDay() {
        grid.querySelector('[tabindex="0"]').focus();
    }

    function shiftMonth(delta) {
        const lastDay = new Date(focus.getFullYear(), focus.getMonth() + delta + 1, 0).getDate();
        focus = new Date(focus.getFullYear(), focus.getMonth() + delta, Math.min(focus.getDate(), lastDay));
        render();
    }

    function open() {
        focus = input.value ? parseDate(input.value) : new Date();
        render();
        calendar.showPopover();
        trigger.setAttribute('aria-expanded', 'true');
        placePopup(trigger, calendar);
        focusDay();
    }

    function close(refocus = true) {
        if (!isOpen()) return;
        calendar.hidePopover();
        trigger.setAttribute('aria-expanded', 'false');
        if (refocus) trigger.focus();
    }

    function choose(iso) {
        input.value = iso;
        input.dispatchEvent(new Event('change', {bubbles: true}));
        close();
    }

    trigger.addEventListener('click', () => isOpen() ? close() : open());
    calendar.addEventListener('click', event => event.preventDefault());
    todayButton.addEventListener('click', () => choose(today()));
    grid.addEventListener('click', event => {
        const day = event.target.closest('.calendar-day');
        if (day) choose(day.dataset.date);
    });
    calendar.addEventListener('keydown', event => {
        const steps = {ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7};
        if (event.key === 'Tab') return close();
        if (event.key === 'Escape') {
            close();
        } else if (event.key === 'PageUp' || event.key === 'PageDown') {
            shiftMonth(event.key === 'PageUp' ? -1 : 1);
            focusDay();
        } else if (event.target.closest('.calendar-grid') && event.key in steps) {
            focus = new Date(focus.getFullYear(), focus.getMonth(), focus.getDate() + steps[event.key]);
            render();
            focusDay();
        } else {
            return;
        }
        event.preventDefault();
    });

    dismissOnOutside(root, calendar, () => close(false));

    input.addEventListener('change', sync);
    input.form.addEventListener('reset', () => setTimeout(sync));
    sync();
}

function count(n, forms) {
    return `${n} ${forms[pluralRules.select(n)] ?? forms.many}`;
}

function parseDate(value) {
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
}

function toIso(date) {
    return new Date(date - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function today() {
    return toIso(new Date());
}

function capitalize(text) {
    return text[0].toUpperCase() + text.slice(1);
}

function groupBy(items, keyOf) {
    const groups = new Map();
    for (const item of items) {
        const key = keyOf(item);
        if (!groups.has(key)) groups.set(key, []);
        groups.get(key).push(item);
    }
    return groups;
}

function readForm(form) {
    const data = {};
    for (const [name, value] of new FormData(form)) {
        const text = value.trim();
        const numeric = form.elements[name].type === 'number' || name.endsWith('Id');
        data[name] = text === '' ? null : numeric ? Number(text) : text;
    }
    return data;
}

function fillForm(form, data) {
    for (const element of form.elements) {
        if (!element.name || !(element.name in data)) continue;
        element.value = data[element.name] ?? '';
        element.dispatchEvent(new Event('change'));
    }
    if (data.id) form.dataset.id = data.id;
}

function openDialog(dialog, data = {}) {
    const form = dialog.querySelector('form');
    form.reset();
    delete form.dataset.id;
    fillForm(form, data);
    dialog.querySelector('h2').textContent = data.id ? dialog.dataset.editTitle : dialog.dataset.createTitle;
    dialog.showModal();
}

function onSubmit(form, action) {
    const button = form.querySelector('[type="submit"]');
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (button.disabled) return;
        button.disabled = true;
        await run(action);
        button.disabled = false;
    });
}

function bindDialog(dialog, save) {
    const form = dialog.querySelector('form');
    onSubmit(form, async () => {
        await save(readForm(form), form.dataset.id);
        dialog.close();
    });
    dialog.querySelector('[data-close]').addEventListener('click', () => dialog.close());
}

function confirmDelete(message) {
    document.getElementById('confirm-text').textContent = message;
    confirmDialog.returnValue = '';
    confirmDialog.showModal();
    return new Promise(resolve => confirmDialog.addEventListener('close',
        () => resolve(confirmDialog.returnValue === 'confirm'), {once: true}));
}

async function loadWorkouts(page = 0) {
    const result = await api(`/workouts?page=${page}&size=${PAGE_SIZE}`);
    workouts = page === 0 ? result.content : [...workouts, ...result.content];
    workoutsPage = page;
    const total = result.totalElements;
    document.getElementById('workouts-count').textContent = total ? count(total, WORKOUT_FORMS) : '';
    document.getElementById('workouts-empty').hidden = total > 0;
    workoutsMore.hidden = page + 1 >= result.totalPages;

    const months = groupBy(workouts, workout => workout.performedOn.slice(0, 7));
    document.getElementById('workout-list').replaceChildren(...[...months].map(([month, items]) =>
        el('section', {className: 'month'},
            el('h2', {className: 'month-title', textContent: monthTitle(month)}),
            el('ul', {className: 'list'}, ...items.map(workoutItem)))));
}

function monthTitle(key) {
    const [year, month] = key.split('-').map(Number);
    return `${capitalize(monthFormat.format(new Date(year, month - 1)))} ${year}`;
}

function workoutItem(workout) {
    const date = parseDate(workout.performedOn);
    const meta = [weekdayFormat.format(date), workout.durationMinutes && `${workout.durationMinutes} мин`]
        .filter(Boolean)
        .join(' · ');
    return el('li', {},
        el('a', {className: 'row', href: `#/workouts/${workout.id}`},
            el('span', {className: 'date-badge'},
                el('span', {className: 'date-day', textContent: date.getDate()}),
                el('span', {className: 'date-month', textContent: MONTHS[date.getMonth()]})),
            el('span', {className: 'row-main'},
                el('span', {className: 'row-title', textContent: workout.title}),
                el('span', {className: 'row-meta', textContent: capitalize(meta)})),
            icon('chevron')));
}

async function openWorkout(id) {
    const hash = location.hash;
    try {
        if (!/^\d+$/.test(id)) throw new Error('Тренировка не найдена');
        await showWorkout(id);
    } catch (e) {
        if (location.hash === hash) location.replace('#/workouts');
        throw e;
    }
}

async function showWorkout(id) {
    const hash = location.hash;
    const [workout, sets] = await Promise.all([api(`/workouts/${id}`), api(`/workouts/${id}/sets`)]);
    if (location.hash !== hash) return;
    currentWorkout = workout;
    const date = parseDate(workout.performedOn);
    document.getElementById('workout-date').textContent =
        `${fullDateFormat.format(date).replace(' г.', '')}, ${weekdayFormat.format(date)}`;
    document.getElementById('workout-title').textContent = workout.title;
    const notes = document.getElementById('workout-notes');
    notes.textContent = workout.notes ?? '';
    notes.hidden = !workout.notes;
    resetSetForm();
    renderSets(sets);
    showView('workout');
}

async function reloadSets() {
    renderSets(await api(`/workouts/${currentWorkout.id}/sets`));
}

function renderSets(sets) {
    const reps = sets.reduce((sum, set) => sum + set.reps, 0);
    const tonnage = sets.reduce((sum, set) => sum + set.reps * (set.weightKg ?? 0), 0);
    const stats = [
        ['Длительность', currentWorkout.durationMinutes ? `${currentWorkout.durationMinutes} мин` : '—'],
        ['Подходы', sets.length],
        ['Повторения', reps],
        ['Тоннаж', `${numberFormat.format(tonnage)} кг`]
    ];
    document.getElementById('workout-stats').replaceChildren(...stats.map(([label, value]) =>
        el('div', {className: 'stat'}, el('dt', {textContent: label}), el('dd', {textContent: value}))));

    document.getElementById('sets-empty').hidden = sets.length > 0;
    const groups = groupBy(sets, set => set.exerciseId);
    document.getElementById('set-groups').replaceChildren(...[...groups.values()].map(group =>
        el('div', {className: 'set-group'},
            el('h3', {textContent: group[0].exerciseName}),
            el('ol', {className: 'set-list'}, ...group.map(setRow)))));
}

function setRow(set, index) {
    const value = set.weightKg == null
        ? count(set.reps, REP_FORMS)
        : `${numberFormat.format(set.weightKg)} кг × ${set.reps}`;
    const row = el('li', {className: 'set-row'},
        el('span', {className: 'set-number', textContent: index + 1}),
        el('span', {className: 'set-value', textContent: value}),
        rowActions(() => editSet(set, row), async () => {
            if (!await confirmDelete('Удалить этот подход?')) return;
            await api(`/sets/${set.id}`, {method: 'DELETE'});
            if (setForm.dataset.id === String(set.id)) resetSetForm();
            await reloadSets();
        }));
    row.classList.toggle('editing', setForm.dataset.id === String(set.id));
    return row;
}

function editSet(set, row) {
    setForm.reset();
    fillForm(setForm, set);
    setSubmit.textContent = 'Сохранить';
    setCancel.hidden = false;
    setForm.reps.focus();
    for (const other of document.querySelectorAll('.set-row')) other.classList.toggle('editing', other === row);
}

function resetSetForm() {
    setForm.reset();
    delete setForm.dataset.id;
    setSubmit.textContent = 'Добавить';
    setCancel.hidden = true;
    for (const row of document.querySelectorAll('.set-row')) row.classList.remove('editing');
}

async function loadExercises() {
    exercises = await api('/exercises');
    renderExercises();
    renderExerciseOptions();
}

function renderExercises() {
    const query = exerciseSearch.value.trim().toLowerCase();
    const visible = exercises.filter(exercise =>
        (!selectedGroup || exercise.muscleGroup === selectedGroup) && exercise.name.toLowerCase().includes(query));
    document.getElementById('exercises-count').textContent = count(exercises.length, EXERCISE_FORMS);
    document.getElementById('exercises-empty').hidden = visible.length > 0;
    document.getElementById('exercise-list').hidden = visible.length === 0;
    document.getElementById('exercise-list').replaceChildren(...visible.map(exerciseRow));
    for (const chip of muscleFilter.children) chip.classList.toggle('active', chip.value === selectedGroup);
}

function exerciseRow(exercise) {
    return el('li', {className: 'row'},
        el('span', {className: 'row-main'},
            el('span', {className: 'row-title', textContent: exercise.name}),
            exercise.description && el('span', {className: 'row-meta', textContent: exercise.description})),
        el('span', {className: `badge ${exercise.muscleGroup.toLowerCase()}`, textContent: MUSCLE_GROUPS[exercise.muscleGroup]}),
        !exercise.common && rowActions(() => openDialog(exerciseDialog, exercise), async () => {
            if (!await confirmDelete(`Удалить упражнение «${exercise.name}»?`)) return;
            await api(`/exercises/${exercise.id}`, {method: 'DELETE'});
            await loadExercises();
        }));
}

function renderExerciseOptions() {
    const selected = setForm.exerciseId.value;
    const byGroup = groupBy(exercises, exercise => exercise.muscleGroup);
    setForm.exerciseId.replaceChildren(
        el('option', {value: '', textContent: 'Упражнение', disabled: true, defaultSelected: true}),
        ...Object.entries(MUSCLE_GROUPS)
            .filter(([group]) => byGroup.has(group))
            .map(([group, label]) => el('optgroup', {label},
                ...byGroup.get(group).map(exercise => el('option', {value: exercise.id, textContent: exercise.name})))));
    if (exercises.some(exercise => String(exercise.id) === selected)) setForm.exerciseId.value = selected;
}

function showView(name) {
    for (const section of document.querySelectorAll('.view')) section.hidden = section.id !== `view-${name}`;
}

function setSignedIn(signedIn) {
    authView.hidden = signedIn;
    for (const element of document.querySelectorAll('[data-signed-in]')) element.hidden = !signedIn;
}

function setAuthMode(register) {
    registering = register;
    document.getElementById('auth-title').textContent = register ? 'Регистрация' : 'Вход';
    document.getElementById('auth-submit').textContent = register ? 'Зарегистрироваться' : 'Войти';
    document.getElementById('auth-hint').textContent = register ? 'Уже есть аккаунт?' : 'Нет аккаунта?';
    authToggle.textContent = register ? 'Войти' : 'Зарегистрироваться';
    authForm.password.autocomplete = register ? 'new-password' : 'current-password';
}

function showAuth() {
    for (const dialog of document.querySelectorAll('dialog[open]')) dialog.close();
    setMenu(false);
    setSignedIn(false);
}

async function enter(account) {
    logoutButton.title = `Выйти (${account.email})`;
    setSignedIn(true);
    await run(loadExercises);
    route();
}

async function clearApiCache() {
    if (!('caches' in window)) return;
    const keys = await caches.keys();
    await Promise.all(keys.filter(key => key.startsWith('api-')).map(key => caches.delete(key)));
}

function route() {
    if (!authView.hidden) return;
    const [, page, id] = location.hash.split('/');
    const view = page === 'exercises' ? 'exercises' : id ? 'workout' : 'workouts';
    for (const link of document.querySelectorAll('.nav a')) {
        link.classList.toggle('active', link.dataset.page === (view === 'exercises' ? 'exercises' : 'workouts'));
    }
    window.scrollTo(0, 0);
    currentWorkout = null;
    if (view === 'workout') {
        showView(null);
        run(() => openWorkout(id));
        return;
    }
    showView(view);
    if (view === 'workouts') run(loadWorkouts);
    if (view === 'exercises') renderExercises();
}

bindDialog(workoutDialog, async (data, id) => {
    const saved = await api(id ? `/workouts/${id}` : '/workouts', {method: id ? 'PUT' : 'POST', body: data});
    if (id) {
        await showWorkout(saved.id);
    } else {
        location.hash = `#/workouts/${saved.id}`;
    }
});

bindDialog(exerciseDialog, async (data, id) => {
    await api(id ? `/exercises/${id}` : '/exercises', {method: id ? 'PUT' : 'POST', body: data});
    await loadExercises();
});

onSubmit(setForm, async () => {
    const id = setForm.dataset.id;
    const body = readForm(setForm);
    await api(id ? `/sets/${id}` : `/workouts/${currentWorkout.id}/sets`, {method: id ? 'PUT' : 'POST', body});
    resetSetForm();
    await reloadSets();
    setForm.reps.focus();
});

setCancel.addEventListener('click', resetSetForm);

onSubmit(authForm, async () => {
    const body = {email: authForm.email.value.trim(), password: authForm.password.value};
    const account = await api(registering ? '/auth/register' : '/auth/login', {method: 'POST', body});
    authForm.reset();
    await enter(account);
});

authToggle.addEventListener('click', () => setAuthMode(!registering));

logoutButton.addEventListener('click', () => run(async () => {
    await api('/auth/logout', {method: 'POST'});
    await clearApiCache();
    exercises = [];
    showAuth();
}));

workoutsMore.addEventListener('click', async () => {
    workoutsMore.disabled = true;
    await run(() => loadWorkouts(workoutsPage + 1));
    workoutsMore.disabled = false;
});

document.getElementById('new-workout').addEventListener('click', () => openDialog(workoutDialog, {performedOn: today()}));
document.getElementById('new-exercise').addEventListener('click', () =>
    openDialog(exerciseDialog, {muscleGroup: selectedGroup || 'CHEST'}));
document.getElementById('edit-workout').addEventListener('click', () => openDialog(workoutDialog, currentWorkout));
document.getElementById('delete-workout').addEventListener('click', () => run(async () => {
    if (!await confirmDelete(`Удалить тренировку «${currentWorkout.title}» вместе со всеми подходами?`)) return;
    await api(`/workouts/${currentWorkout.id}`, {method: 'DELETE'});
    location.hash = '#/workouts';
}));

for (const dialog of document.querySelectorAll('dialog')) {
    dialog.addEventListener('click', event => {
        if (event.target === dialog) dialog.close();
    });
}

exerciseSearch.addEventListener('input', renderExercises);
muscleFilter.append(...Object.entries({'': 'Все', ...MUSCLE_GROUPS}).map(([value, label]) =>
    el('button', {type: 'button', className: 'chip', value, textContent: label})));
muscleFilter.addEventListener('click', event => {
    if (!event.target.matches('.chip')) return;
    selectedGroup = event.target.value;
    renderExercises();
});

exerciseDialog.querySelector('select').append(...Object.entries(MUSCLE_GROUPS).map(([value, label]) =>
    el('option', {value, textContent: label})));

for (const select of document.querySelectorAll('select')) enhanceSelect(select);
for (const input of document.querySelectorAll('input[type="date"]')) enhanceDate(input);

document.getElementById('theme-toggle').addEventListener('click', () => {
    const root = document.documentElement;
    const systemDark = matchMedia('(prefers-color-scheme: dark)').matches;
    const current = root.dataset.theme ?? (systemDark ? 'dark' : 'light');
    const next = current === 'dark' ? 'light' : 'dark';
    root.dataset.theme = next;
    localStorage.setItem('theme', next);
    for (const meta of document.querySelectorAll('meta[name="theme-color"]')) {
        meta.content = next === 'dark' ? '#111110' : '#f7f7f5';
        meta.removeAttribute('media');
    }
});

const nav = document.getElementById('nav');
const menuToggle = document.getElementById('menu-toggle');

function setMenu(open) {
    nav.classList.toggle('open', open);
    menuToggle.setAttribute('aria-expanded', open);
}

menuToggle.addEventListener('click', event => {
    event.stopPropagation();
    setMenu(!nav.classList.contains('open'));
});
document.addEventListener('click', event => {
    if (!nav.contains(event.target) || event.target.closest('a')) setMenu(false);
});
document.addEventListener('keydown', event => {
    if (event.key === 'Escape') setMenu(false);
});

window.addEventListener('hashchange', route);
api('/auth/me').then(enter, showAuth);

if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js');
