package com.ttl.internal.vn.tool.builder.component.dialog;

import java.awt.BorderLayout;
import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;

import com.ttl.internal.vn.tool.builder.component.IDialog;
import com.ttl.internal.vn.tool.builder.component.Session;
import com.ttl.internal.vn.tool.builder.component.ValidationException;
import com.ttl.internal.vn.tool.builder.component.fragment.SpinningCircle;
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

@Setter
@Getter
public class GitCloneDialog extends JDialog implements IDialog {
    public enum Status {
        CANCEL,
        OK,
        ERROR
    }

    private static class GitCloneProgressMonitor implements ProgressMonitor {
        private final GitCloneDialog dialog;
        private int totalWork;
        private int totalWorkDone;
        private TextProgressMonitor textProgressMonitor;
        private int currentTotalWork;
        private String currentTitle;

        public GitCloneProgressMonitor(GitCloneDialog dialog, Writer outputWriter) {
            this.dialog = dialog;
            this.textProgressMonitor = new TextProgressMonitor(outputWriter);
            this.currentTotalWork = UNKNOWN;
        }

        private int getProgress() {
            return (int) Math.round(100. * totalWorkDone / totalWork);
        }

        @Override
        public void beginTask(String title, int totalWork) {
                currentTotalWork = totalWork;
            this.currentTitle = title;
            if (currentTotalWork != UNKNOWN) {
                this.totalWork = currentTotalWork;
                this.totalWorkDone = 0;
            } else {
                this.totalWork = 1;
                this.totalWorkDone = 1;
            }
            SwingGraphicUtil.updateUI(() -> {
                dialog.getProgressBar().setValue(getProgress());
                dialog.getProgressBar().setStatus(currentTitle + ": " + getProgress() + "%");
            });
            textProgressMonitor.beginTask(currentTitle, totalWork);
        }

        @Override
        public void update(int completed) {
            if (currentTotalWork != UNKNOWN) {
                totalWorkDone += completed;
            }
            SwingGraphicUtil.updateUI(() -> {
                dialog.getProgressBar().setValue(getProgress());
                dialog.getProgressBar().setStatus(currentTitle + ": " + getProgress() + "%");
            });
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
            return textProgressMonitor.isCancelled();
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
    private Status status;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField repoField;
    private Button cancelBtn;
    private Button cloneBtn;
    private FileField cloneFileField;
    private ProgressBar progressBar;
    private SpinningCircle progressCircle;
    private JFrame frame;
    private transient InputGroup inputGroup;
    private transient InputGroup inputGroup2;

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

                    final String finalUsername = username;
                    final String finalPassword = password;
                    SwingGraphicUtil.run(() -> {
                        try {
                            GitUtil.cloneGitRepo(repoField.getText(), cloneFileField.getSelectedFile(),
                            finalUsername,
                            finalPassword,
                            new GitCloneProgressMonitor(this, new PrintWriter(System.out)));
                            Session.getInstance().setGitPassword(finalPassword);
                            Session.getInstance().setGitUsername(finalUsername);
                            this.status = Status.OK;
                            this.dispose();
                        } catch (GitAPIException e1) {
                            this.status = Status.ERROR;
                            handleException(e1);
                        } finally {
                            inputGroup.setEditable(true);
                            inputGroup2.setEditable(true);
                            cloneBtn.setEnabled(true);
                        }
                    });
                } else {
                    throw new ValidationException(errors.get(0));
                }
            } catch (Exception ex) {
                this.status = Status.ERROR;
                handleException(ex);
            }
        });
        cancelBtn.addActionListener(e -> dispose());
    }

    @Override
    public int showDialog() {
        setVisible(true);
        if (status == null) {
            status = Status.CANCEL;
        }
        return status.ordinal();
    }

    @Override
    public void handleException(Exception e) {
        this.status = Status.CANCEL;
        logger.error(e.getMessage(), e);
        JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        if (e instanceof ValidationException) {
            ((ValidationException) e).getError().getSrc().requestFocus();
        }
    }

    public File getClonedFolder() {
        return cloneFileField.getSelectedFile();
    }
}
