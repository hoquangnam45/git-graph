package com.ttl.internal.vn.tool.builder.component.dialog;

import java.awt.BorderLayout;
import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.ttl.internal.vn.tool.builder.task.DefaultSubscriber;
import com.ttl.internal.vn.tool.builder.task.ITaskController;
import com.ttl.internal.vn.tool.builder.task.Task;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;

import com.ttl.internal.vn.tool.builder.component.IDialog;
import com.ttl.internal.vn.tool.builder.component.Session;
import com.ttl.internal.vn.tool.builder.component.ValidationException;
import com.ttl.internal.vn.tool.builder.component.input.Button;
import com.ttl.internal.vn.tool.builder.component.input.FileField;
import com.ttl.internal.vn.tool.builder.component.input.InputGroup;
import com.ttl.internal.vn.tool.builder.component.input.PasswordField;
import com.ttl.internal.vn.tool.builder.component.input.ProgressBar;
import com.ttl.internal.vn.tool.builder.component.input.TextField;
import com.ttl.internal.vn.tool.builder.component.input.TextValidator;
import com.ttl.internal.vn.tool.builder.component.input.ValidatorError;
import com.ttl.internal.vn.tool.builder.util.GitUtil;
import com.ttl.internal.vn.tool.builder.util.SwingGraphicUtil;
import com.ttl.internal.vn.tool.builder.util.GitUtil.CredentialEntry;

import lombok.Getter;
import lombok.Setter;
import org.eclipse.jgit.transport.Transport;

import static java.util.function.Predicate.not;
import static org.eclipse.jgit.lib.ProgressMonitor.UNKNOWN;

@Setter
@Getter
// NOTE: Kill transport
public class GitCloneDialog extends JDialog implements IDialog {
    private static class GitCloneProgressMonitor implements ProgressMonitor {
        private final GitCloneTask gitCloneTask;
        private final TextProgressMonitor textProgressMonitor;

        public GitCloneProgressMonitor(GitCloneTask gitCloneTask, Writer outputWriter) {
            this.gitCloneTask = gitCloneTask;
            this.textProgressMonitor = new TextProgressMonitor(outputWriter);
        }
        
        @Override
        public void beginTask(String title, int totalWork) {
            gitCloneTask.beginTask(new GitCloneSubTask(title, totalWork));
            textProgressMonitor.beginTask(title, totalWork);
        }

        @Override
        public void update(int completed) {
            gitCloneTask.update(completed);
            textProgressMonitor.update(completed);
        }

        @Override
        public void endTask() {
            textProgressMonitor.endTask();
        }

        @Override
        public void start(int totalTasks) {
            textProgressMonitor.start(totalTasks);
        }

        @Override
        public boolean isCancelled() {
            return gitCloneTask.isCancel();
        }

        @Override
        public void showDuration(boolean enabled) {
            textProgressMonitor.showDuration(enabled);
        }
    }

    public GitCloneDialog(JFrame frame) {
        super();
        this.frame = frame;
        this.inputGroup = new InputGroup();
        this.inputGroup2 = new InputGroup();
        initUI();
        registerListeners();
    }

    private transient Logger logger = LogManager.getLogger(GitCloneDialog.class);
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField repoField;
    private Button cancelBtn;
    private Button cloneBtn;
    private FileField cloneFileField;
    private ProgressBar progressBar;
    private JFrame frame;
    private transient InputGroup inputGroup;
    private transient InputGroup inputGroup2;
    private GitCloneTask gitCloneTask;

    private static final int LABEL_LENGTH = 120;
    private static final int INPUT_LENGTH = 300;

    @Override
    public void initUI() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel mainPanel = new JPanel();
        GroupLayout mainGroupLayout = new GroupLayout(mainPanel);
        mainPanel.setLayout(mainGroupLayout);
        mainGroupLayout.setAutoCreateContainerGaps(true);
        mainGroupLayout.setAutoCreateGaps(true);

        List<TextValidator> validators = Arrays.asList(TextField.Validator.notBlank());
        usernameField = new TextField("Username", LABEL_LENGTH, INPUT_LENGTH, validators);
        passwordField = new PasswordField("Password", LABEL_LENGTH, INPUT_LENGTH, validators);
        repoField = new TextField("Repository", LABEL_LENGTH, INPUT_LENGTH, validators);
        cloneFileField = new FileField("Clone directory", LABEL_LENGTH, INPUT_LENGTH, JFileChooser.DIRECTORIES_ONLY,
                validators);
        inputGroup2.addInput(usernameField);
        inputGroup2.addInput(passwordField);
        inputGroup.addInput(repoField);
        inputGroup.addInput(cloneFileField);

        Group inputLayoutHGroup = mainGroupLayout.createParallelGroup()
                .addComponent(usernameField)
                .addComponent(passwordField)
                .addComponent(repoField)
                .addComponent(cloneFileField);
        Group inputLayoutVGroup = mainGroupLayout.createSequentialGroup()
                .addComponent(usernameField)
                .addComponent(passwordField)
                .addComponent(repoField)
                .addComponent(cloneFileField);

        cancelBtn = new Button("Cancel");
        cloneBtn = new Button("Clone");

        Group buttonLayoutHGroup = mainGroupLayout.createSequentialGroup()
                .addComponent(cloneBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE)
                .addComponent(cancelBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE);

        Group buttonLayoutVGroup = mainGroupLayout.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(cloneBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE)
                .addComponent(cancelBtn, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                        GroupLayout.PREFERRED_SIZE);

        progressBar = new ProgressBar();

        mainGroupLayout.setVerticalGroup(mainGroupLayout.createSequentialGroup()
                .addGroup(inputLayoutVGroup)
                .addGap(30)
                .addGroup(buttonLayoutVGroup));

        mainGroupLayout.setHorizontalGroup(mainGroupLayout.createParallelGroup()
                .addGroup(mainGroupLayout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addGroup(inputLayoutHGroup)
                        .addGroup(buttonLayoutHGroup)));

        mainPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(mainPanel, BorderLayout.NORTH);
        getContentPane().add(progressBar, BorderLayout.SOUTH);

        setTitle("Git Clone Dialog");
        setLocationRelativeTo(frame);
        setModalityType(ModalityType.APPLICATION_MODAL);
        setResizable(false);
        pack();
    }

    @Override
    public void refreshUI() {
        /** noop */
    }

    @Override
    public void registerListeners() {
        cloneBtn.addActionListener(e -> {
            try {
                cloneBtn.setEnabled(false);
                inputGroup.setEditable(false);
                inputGroup2.setEditable(false);
                List<ValidatorError> errors = inputGroup.validate();
                if (StringUtils.isNotBlank(usernameField.getText())
                        || StringUtils.isNotBlank(passwordField.getText())) {
                    errors.addAll(inputGroup2.validate());
                }
                if (errors.isEmpty()) {
                    String username = null;
                    String password = null;
                    if (Session.getInstance().isUseGitCredential()
                            && StringUtils.isBlank(usernameField.getText())) {
                        List<CredentialEntry> credentials = Session.getInstance()
                                .scanCredential(repoField.getText());
                        CredentialEntry chosenCredentialEntry = null;
                        for (CredentialEntry credentialEntry : credentials) {
                            String repo = GitUtil.getRepo(credentialEntry.getUrl());
                            if (chosenCredentialEntry == null || StringUtils.isNotBlank(repo)
                                    && StringUtils.isBlank(GitUtil.getRepo(chosenCredentialEntry.getUrl()))) {
                                chosenCredentialEntry = credentialEntry;
                            }
                        }
                        if (chosenCredentialEntry != null) {
                            username = chosenCredentialEntry.getUsername();
                            password = chosenCredentialEntry.getPassword();
                            usernameField.setText(username);
                            passwordField.setText(password);
                        }
                    } else {
                        username = usernameField.getText();
                        password = passwordField.getText();
                    }

                    GitUtil.checkLogin(username, password, repoField.getText());

                    if (StringUtils.isNotBlank(username)) {
                        Session.getInstance().setCredentialEntry(CredentialEntry.builder()
                                .username(username)
                                .password(password)
                                .url(repoField.getText())
                                .build());
                    }

                    
                    this.gitCloneTask = new GitCloneTask(usernameField.getText(), passwordField.getText(), repoField.getText(), cloneFileField.getSelectedFile(), this);
                    gitCloneTask.subscribe(new DefaultSubscriber<>() {
                        @Override
                        public void onComplete() {
                            try {
                                Session.getInstance().setGitPassword(usernameField.getText());
                                Session.getInstance().setGitUsername(passwordField.getText());
                                gitCloneTask.getDialog().dispose();
                            } finally {
                                inputGroup.setEditable(true);
                                inputGroup2.setEditable(true);
                                cloneBtn.setEnabled(true);
                            }
                        }
                    });
                    gitCloneTask.start();
                } else {
                    throw new ValidationException(errors.get(0));
                }
            } catch (Exception ex) {
                handleException(ex);
            }
        });
        cancelBtn.addActionListener(e -> {
            Optional.ofNullable(gitCloneTask).ifPresent(GitCloneTask::cancel);
            dispose();
        });
    }

    @Override
    public int showDialog() {
        setVisible(true);
        return Optional.ofNullable(gitCloneTask).map(GitCloneTask::status).orElse(Task.TaskStatus.CANCEL.ordinal());
    }

    @Override
    public void handleException(Exception e) {
        logger.error(e.getMessage(), e);
        gitCloneTask.stopExceptionally(e);
        JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        if (e instanceof ValidationException) {
            ((ValidationException) e).getError().getSrc().requestFocus();
        }
    }

    public File getClonedFolder() {
        return cloneFileField.getSelectedFile();
    }
    
    public static class GitCloneSubTask extends Task implements ITaskController {
        private int doneWork;

        private Flow.Subscriber<? super Task> subscriber;

        private TaskStatus status = TaskStatus.NOT_START;
        private final int totalWork;
        private final String title;
        
        public GitCloneSubTask(String title, int totalWork) {
            super();
            this.totalWork = totalWork;
            this.title = title;
        }

        @Override
        public boolean update(int doneWork) {
            if (isStop()) {
                return false;
            }
            this.doneWork += doneWork;
            if (this.doneWork == totalWork) {
                status = TaskStatus.DONE;
            }
            Optional.ofNullable(subscriber).ifPresent(l -> l.onNext(this));
            return true;
        }

        @Override
        public boolean done() {
            this.status = TaskStatus.DONE;
            Optional.ofNullable(subscriber).ifPresent(Flow.Subscriber::onComplete);
            return true;
        }
        
        @Override
        public String explainTask() {
            return title;
        }

        @Override
        public int totalWork() {
            if (totalWork != UNKNOWN) {
                return totalWork;
            } else {
                return 1;
            }
        }

        @Override
        public int doneWork() {
            if (totalWork != UNKNOWN) {
                return doneWork;
            } else {
                return 0;
            }
        }

        @Override
        public int status() {
            return status.ordinal();
        }

        @Override
        public boolean start() {
            this.status = TaskStatus.IN_PROGRESS;
            return true;
        }

        @Override
        public boolean stopExceptionally(Throwable e) {
            this.status = TaskStatus.ERROR;
            return true;
        }

        @Override
        public boolean cancel() {
            this.status = TaskStatus.CANCEL;
            return true;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super Task> subscriber) {
            this.subscriber = subscriber;
        }
    }
    
    @Getter
    public static class GitCloneTask extends Task implements ITaskController {
        private final String username;
        private final String password;
        private final String uri;
        private final File targetFolder;
        private final GitCloneDialog dialog;
        private final List<GitCloneSubTask> subtasks;
        private Transport transport;

        private GitCloneSubTask currentTask;
        private Flow.Subscriber<? super Task> subscriber;
        private boolean cancel;
        private boolean error;
        
        public GitCloneTask(String username, String password, String uri, File targetFolder, GitCloneDialog dialog) {
            this.subtasks = new Vector<>();
            this.username = username;
            this.password = password;
            this.uri = uri;
            this.targetFolder = targetFolder;
            this.dialog = dialog;
        }

        public void beginTask(GitCloneSubTask currentTask) {
            // Done previous task
            Optional.ofNullable(this.currentTask).ifPresent(GitCloneSubTask::done);
            this.currentTask = currentTask;
            this.subtasks.add(currentTask);
            // Start new task
            currentTask.subscribe(new DefaultSubscriber<>() {
                @Override
                public void onNext(Task item) {
                    SwingGraphicUtil.updateUI(() -> {
                        int percentage = (int) Math.round(percentage());
                        dialog.getProgressBar().setValue(percentage);
                        dialog.getProgressBar().setStatus(explainTask() + ": " + percentage + "%");
                    });
                }
            });
            currentTask.start();
        }
        
        @Override
        public String explainTask() {
            return Optional.ofNullable(currentTask).map(Task::explainTask).orElse(null);
        }

        @Override
        public int totalWork() {
            return subtasks.stream().map(it -> it.scaling() * it.totalWork()).map(Math::ceil).map(Integer.class::cast).reduce(0, Integer::sum);
        }

        @Override
        public int doneWork() {
            return subtasks.stream().filter(it -> it.status() != TaskStatus.IN_PROGRESS.ordinal()).map(it -> it.scaling() * it.totalWork()).map(Math::ceil).map(Integer.class::cast).reduce(0, Integer::sum);
        }

        @Override
        public int status() {
            if (error) {
                return TaskStatus.ERROR.ordinal();
            }
            if (cancel) {
                return TaskStatus.CANCEL.ordinal();
            }
            boolean inProgress = subtasks.stream().map(Task::status).anyMatch(it -> it == TaskStatus.IN_PROGRESS.ordinal());
            if (inProgress) {
                return TaskStatus.IN_PROGRESS.ordinal();
            }
            return TaskStatus.NOT_START.ordinal();
        }

        @Override
        public boolean cancel() {
            // NOTE: Settting cancel will cause side-effect to ProgressMonitor
            cancel = true;
            subtasks.forEach(GitCloneSubTask::cancel);
            return true;
        }

        @Override
        public boolean start() {
            SwingGraphicUtil.run(() -> {
                try {
                    GitUtil.cloneGitRepo(uri, targetFolder, username, password, new GitCloneProgressMonitor(this, new PrintWriter(System.out)));
                    subscriber.onComplete();
                } catch (GitAPIException e) {
                    throw new RuntimeException(e);
                }
            });
            return true;
        }
        
        @Override
        public boolean stopExceptionally(Throwable e) {
            cancel();
            error = true;
            subtasks.forEach(subtask -> subtask.stopExceptionally(e));
            subscriber.onError(e);
            return true;
        }
        
        @Override
        public double percentage() {
            return Optional.ofNullable(currentTask).map(Task::percentage).orElse(0.);
        }
        
        @Override
        public boolean update(int doneWork) {
            Optional.ofNullable(currentTask).ifPresent(it -> {
                it.update(doneWork);
                subscriber.onNext(this);
            });
            return true;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super Task> subscriber) {
            this.subscriber = subscriber;
        }
    }
}
